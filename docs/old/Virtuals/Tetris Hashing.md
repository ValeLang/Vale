# Tetris Hashing

Evan Ovadia

This doc describes a [[perfect
hashing]{.underline}](https://en.wikipedia.org/wiki/Perfect_hash_function)
algorithm called Tetris Hashing.

Tetris Hashing is a two-tiered hashing approach, which generates very
wide, sparse secondary hash tables, but then overlaps them so the
combined memory usage is very dense.

It has linear average space overhead (0.66n words) and linear average
runtime, and its main benefit is its fast hash function: it always takes
only 13 assembly instructions to look up a value.

[[Tetris Hashing]{.underline}](#tetris-hashing)

> [[Motivation]{.underline}](#motivation)
>
> [[Background]{.underline}](#background)
>
> [[Perfect Hashing]{.underline}](#perfect-hashing)
>
> [[Tiered Perfect Hashing]{.underline}](#tiered-perfect-hashing)
>
> [[Double Modulus Hashing]{.underline}](#double-modulus-hashing)
>
> [[Tetris Hashing]{.underline}](#tetris-hashing-1)
>
> [[Linear Tetris]{.underline}](#linear-tetris)
>
> [[Bucket Groups]{.underline}](#bucket-groups)
>
> [[Optimizations]{.underline}](#optimizations)
>
> [[Distributing Buckets Evenly Into
> Groups]{.underline}](#distributing-buckets-evenly-into-groups)
>
> [[Linear Tetris on Large Buckets
> First]{.underline}](#linear-tetris-on-large-buckets-first)
>
> [[Choose the Best Number of
> Buckets]{.underline}](#choose-the-best-number-of-buckets)
>
> [[Mulligans]{.underline}](#mulligans)
>
> [[Performance]{.underline}](#performance)
>
> [[Future Work]{.underline}](#future-work)

## Motivation

Some applications need hash sets to have extremely fast lookup times,
and know all their values beforehand. These applications benefit from
using perfect hash maps, because they have no collisions, which means
lookup time is O(1), rather than the normal hash map's O(N) lookup time.

Existing algorithms such as Compress, Hash, and Displace generate very
compact tables with very fast lookup functions.

However, many applications would benefit from a hashing method which has
faster lookup time, at the expense of more space overhead.

This doc explores such a method, called Tetris Hashing. Tetris hashing
has linear average space overhead, linear average runtime, and an
extremely fast lookup function: 13 assembly instructions.

## Background

### Perfect Hashing

There is a special class of hash set, called a "perfect hash set", which
guarantees no collisions. This means that instead of worst case O(N)
lookup time, lookup time is O(1).

For example, if we had the following nine values: 88, 27, 13, 54, 75,
46, 9, 0, 42, a perfect hash set would be:

-   Hash function f(x) = x + 7

-   Hash table: \[13, 54, 75, 46, 27, 88, 9, 0, (null), 42\]

So, for example, if we want to check if 27 is in the hash set:

-   Plug it into the hash function, 27 + 7 -\> 34

-   Modulus it by the table size, 34 % 10 -\> 4

-   Get the number at that index in the table (27)

-   Compare it to the input number; 27 == 27.

Since we found the 27, we know that 27 is a member of this hash set.

Another example: we want to check if 79 is in the hash set:

-   Plug it into the hash function, 79 + 7 -\> 86

-   Modulus it by the table size, 86 % 10 -\> 6

-   Get the number at that index in the table (9)

-   Compare it to the input number; 79 != 9

Since we found a 9 where the 79 would have been, we know 79 isn't a
member of the hash set.

Notice how there was a (null) in the hash table. One theoretically could
craft a very intricate hash function so that we only need a 9-size table
for our 9 elements. This is called "minimal perfect hashing".

### Tiered Perfect Hashing

Many perfect hash sets are actually hash tables of hash tables; in other
words, the main hash table points to many secondary hash tables (also
known as "buckets"). Each secondary hash table has its own hash
function.

For example, we might have a hash set with a hash function f(x) = x + 7,
with four secondary hash tables like this:

-   f(x) = x \* 2, \[17, 29, 25\]

-   f(x) = x \* 5, \[30, 26, 22\],

-   f(x) = x \^ 15, \[null, null, 71, 19, 79\]

-   f(x) = x \* 13, \[20, null\]

To see if the hash set contains a number X:

-   Run it through the top hash function (in this case, f(x) = x + 7),
    > and modulus that by the top table's size, to determine which
    > secondary hash table to look in.

-   Run it through the secondary hash table's function, and modulus that
    > by the secondary hash table's size, to determine where in the
    > secondary hash table the element would be.

-   Check the value at that spot in the secondary hash table, compare it
    > to X.

For example, if we want to see if 71 is in the hash set:

-   Run it through f(x) = x + 7, to get 78. Modulus it by the top
    > table's size (4) to get 2. This means we'll look inside the 2th
    > secondary hash table.

-   Run it through f(x) = x \^ 15, to get 72. Modulus it by the
    > secondary hash table's size (5) to get 2. This means we'll look at
    > index 2 of the secondary hash table.

-   Check the value at index 2 in the secondary hash table, which
    > happens to be 71. That's what we're looking for, which means 71
    > was indeed in the hash set.

## Double Modulus Hashing

Tetris Hashing builds on something we'll call "Double Modulus Hashing".
Double Modulus Hashing is a very simple form of Tiered Perfect Hashing;
in other words, we have a top table, which points to many secondary
tables.

We start with these details:

-   The top level is size N/4, and therefore we have N/4 secondary
    > tables.

-   It always uses an extremely simple hash function for all of its hash
    > tables: **f(x) = x**. In other words, Double Modulus Hashing
    > doesn't have hash functions.

These are very unintuitive, and don't make for a very space-efficient
set, but it will be improved below.

To construct a hash set like this:

-   First, split all the elements by which secondary hash table they
    > would be in, this should produce N/4 lists.

-   To make each list into a secondary hash table:

    -   Let M be the number of elements in the bucket.

    -   Repeat:

        -   Make an array of size M, and try hashing all M elements into
            > the bucket.

        -   If there were no collisions, stop.

        -   If there were collisions, increase M by 1, and try again.

Double Modulus Hashing has O(1) lookup time. In fact, it only takes 13
assembly instructions to look something up. Given some structures like
this:

> class HashSet\<KeyType\> {
>
> int numSecondaryTables;
>
> SecondaryTable\<KeyType\>\[0\] secondaryTables;
>
> }
>
> class SecondaryTable\<KeyType\> {
>
> int secondaryTableSize;
>
> Nullable\<KeyType\>\[0\] members;
>
> }

and given a HashSet\<Int\> pointer "myHashSetPtr", we would look up the
member 1234812348 with this code:

> int numSecondaryTables = myHashSetPtr-\>numSecondaryTables;
>
> int secondaryTableIndex = 1234812348 % numSecondaryTables;
>
> SecondaryTable\<Int\>\* secondaryTable =
>
> myHashSetPtr-\>secondaryTables\[secondaryTableIndex\];
>
> int secondaryTableSize = secondaryTable-\>size;
>
> int indexInSecondaryTable = 1234812348 % secondaryTableSize;
>
> int foundValue = secondaryTable-\>members\[indexInSecondaryTable\];
>
> bool found = foundValue == 1234812348;

These map to the below assembly pseudocode instructions (assuming 64
bit):

> numScondaryTables = load myHashSet
>
> secondaryTableIndex = 1234812348 % numSecondaryTables
>
> secondaryTablesBeginPtr = myHashSetPtr + 8
>
> offsetInSecondaryTables = 8 \* secondaryTableIndex
>
> secondaryTablePtrPtr = secondaryTablesBeginPtr +
> offsetInSecondaryTables
>
> secondaryTablePtr = load secondaryTablePtrPtr
>
> secondaryTableSize = load secondaryTablePtr
>
> indexInSecondaryTable = secondaryTableSize % 1234812348
>
> membersBeginPtr = secondaryTableSize + 8
>
> offsetInMembers = 8 \* indexInSecondaryTable
>
> memberPtr = membersBeginPtr + offsetInMembers
>
> foundValue = load memberPtr
>
> found = foundValue == 1234812348

This is only 13 instructions in total. Notice also how there is no
branching involved, no for-loops, nothing that would slow this down.

Double Modulus Hashing meets our first goal, of having O(1) lookup time.
In fact, looking at the benchmarks below, it also has linear average
space usage and linear average run time, so it would seem to be a pretty
good hashing method.

However, Double Modulus Hashing has a crippling drawback: its space
usage. It is linear, but a very large linear: we use about 2n words in
overhead. The Tetris Hashing approach fixes this.

Here's a table showing how large this hash set can get:

+----+--------------+----------+--------------+-----------+-----------+
| ** | **Average**  | *        | **Average**  | **Worst** | **        |
| \# |              | *Worst** |              |           | Average** |
| I  | **Space**    |          | **Overhead** | **O       |           |
| Ds |              | *        |              | verhead** | **Run     |
| ** |              | *Space** |              |           | Time**    |
+====+==============+==========+==============+===========+===========+
| ** | 2930.077     | 3155     | 1930.077     | 2155      | 4.124     |
| 10 | (2.930n)     | (3.155n) | (1.930n)     | (2.155n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 6000.066     | 6262     | 4000.066     | 4262      | 6.837     |
| 20 | (3.000n)     | (3.131n) | (2.000n)     | (2.131n)  | (0.003n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 9801.168     | 10180    | 6801.168     | 7180      | 11.125    |
| 30 | (3.267n)     | (3.393n) | (2.267n)     | (2.393n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 12011.034    | 12360    | 8011.034     | 8360      | 14.464    |
| 40 | (3.003n)     | (3.090n) | (2.003n)     | (2.090n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 14651.947    | 15043    | 9651.947     | 10043     | 19.587    |
| 50 | (2.930n)     | (3.009n) | (1.930n)     | (2.009n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 20388.304    | 21038    | 14388.304    | 15038     | 26.010    |
| 60 | (3.398n)     | (3.506n) | (2.398n)     | (2.506n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 21955.248    | 22547    | 14955.248    | 15547     | 29.350    |
| 70 | (3.136n)     | (3.221n) | (2.136n)     | (2.221n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 24023.870    | 24620    | 16023.870    | 16620     | 32.804    |
| 80 | (3.003n)     | (3.078n) | (2.003n)     | (2.078n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| ** | 29532.927    | 30218    | 20532.927    | 21218     | 40.479    |
| 90 | (3.281n)     | (3.358n) | (2.281n)     | (2.358n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+
| *  | 29996.218    | 30519    | 19996.218    | 20519     | 43.393    |
| *1 | (3.000n)     | (3.052n) | (2.000n)     | (2.052n)  | (0.004n)  |
| 00 |              |          |              |           |           |
| 00 |              |          |              |           |           |
| ** |              |          |              |           |           |
+----+--------------+----------+--------------+-----------+-----------+

Notes about the above table:

-   1000 trials per row.

-   "Space" is how many words were needed altogether, including the top
    > hash table and the bucket hash tables.

-   "Overhead" is how many words were needed, including the top hash
    > table and the unused spots in the bucket hash tables. It's always
    > N less than Space.

-   Run time is in milliseconds.

-   [[Source
    > here]{.underline}](https://github.com/Verdagon/TetrisTables/blob/master/src/LeveledTableGenerator.scala)

## Tetris Hashing

Tetris Hashing is when we apply an algorithm called Linear Tetris to all
of the secondary tables in a Double Modulus Hash set. This make the 2n
words of overhead into something more reasonable, like 0.66n.

### Linear Tetris

Remember that a Tiered Perfect Hash is a hash set of secondary hash
sets. For this section, we'll pretend we have 11 elements, spread among
9 secondary hash sets, which we'll call "buckets":

-   Bucket 0 -\> \[null, G, null, null, null, C\]

-   Bucket 1 -\> \[\]

-   Bucket 2 -\> \[\]

-   Bucket 3 -\> \[F, null, D, null, J\]

-   Bucket 4 -\> \[\]

-   Bucket 5 -\> \[A\]

-   Bucket 6 -\> \[\]

-   Bucket 7 -\> \[null, null, B\]

-   Bucket 8 -\> \[H, null, I, K\]

The above set of data can also be illustrated like below. The coloring
is to help us see what bucket the elements are associated with.

**\[ 0 G 0 0 0 C F 0 D 0 J A 0 0 B H 0 I K \]**

**↑ ↑ ↑ ↑ ↑**

**0 1,2,3 5 6,7 8**

Altogether, these buckets use up 19 spaces. How can we reduce this
space?

We can do something clever: **overlap them in memory!** For example,
first, let's move the **8** bucket to the very beginning spaces, because
it fits there:

**\[ H G I K 0 C F 0 D 0 J A 0 0 B \]**

**↑ ↑ ↑ ↑**

**0,8 1,2,3 5 6,7**

Notice how the **8** overlaps the **0** bucket. That's fine, as long as
the **H** , **I** , and the **K** are 0, 2, and 3 spaces from where
**8** is pointing, and we don't interfere with any of **0**'s elements.

We can go further! Let's move the **7** now, to fill that first gap.

**\[ H G I K B C F 0 D 0 J A \]**

**↑ ↑ ↑ ↑ ↑**

**0,8 7 1,2,3 5 6**

Notice how the **7** is pointing two spaces before the **B** , as it was
before the move. As long as the **B** stays 2 spaces after the **7** as
it was before the move, then the move is valid.

Now let's move the **5** to fill another gap:

**\[ H G I K B C F A D 0 J \]**

**↑ ↑ ↑ ↑ ↑**

**0,8 7 1,2,3 5 6**

Altogether, the entire thing is now 11 spaces, much better than the
original 19!

In summary, we can **combine** our secondary hash sets into one "Giant
Combined Secondary Table", using linear tetris.

See the following benchmarks, the space usage is much smaller now, an
average overhead of about 0.66 rather than 2.0.

+----+------------+----------+-------------+-----------+-------------+
| ** | *          | *        | **Average** | **Worst** | **Average** |
| \# | *Average** | *Worst** |             |           |             |
| I  |            |          | *           | **O       | **Run       |
| Ds | **Space**  | *        | *Overhead** | verhead** | Time**      |
| ** |            | *Space** |             |           |             |
+====+============+==========+=============+===========+=============+
| ** | 1554.63    | 1579     | 554.63      | 579       | 80.38       |
| 10 | (1.555n)   | (1.579n) | (0.555n)    | (0.579n)  | (0.08n)     |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 3091.62    | 3124     | 1091.62     | 1124      | 294.67      |
| 20 | (1.546n)   | (1.562n) | (0.546n)    | (0.562n)  | (0.147n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 4626.04    | 4664     | 1626.04     | 1664      | 702.08      |
| 30 | (1.542n)   | (1.555n) | (0.542n)    | (0.555n)  | (0.234n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 6164.48    | 6219     | 2164.48     | 2219      | 1231.15     |
| 40 | (1.541n)   | (1.555n) | (0.541n)    | (0.555n)  | (0.308n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 7728.55    | 7790     | 2728.55     | 2790      | 2264.7      |
| 50 | (1.546n)   | (1.558n) | (0.546n)    | (0.558n)  | (0.453n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 9152.79    | 9185     | 3152.79     | 3185      | 3405.48     |
| 60 | (1.525n)   | (1.531n) | (0.525n)    | (0.531n)  | (0.568n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 10731.95   | 10813    | 3731.95     | 3813      | 4578.9      |
| 70 | (1.533n)   | (1.545n) | (0.533n)    | (0.545n)  | (0.654n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 12305.18   | 12379    | 4305.18     | 4379      | 5898.96     |
| 80 | (1.538n)   | (1.547n) | (0.538n)    | (0.547n)  | (0.737n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| ** | 13796.52   | 13854    | 4796.52     | 4854      | 7589.15     |
| 90 | (1.533n)   | (1.539n) | (0.533n)    | (0.539n)  | (0.843n)    |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+
| *  | 15362.45   | 15430    | 5362.45     | 5430      | 9170.07     |
| *1 | (1.536n)   | (1.543n) | (0.536n)    | (0.543n)  | (0.917n)    |
| 00 |            |          |             |           |             |
| 00 |            |          |             |           |             |
| ** |            |          |             |           |             |
+----+------------+----------+-------------+-----------+-------------+

Notes about the above table:

-   100 trials per row rather than the usual 1000, because it took so
    > long. The optimized version's table further below has 1000 trials
    > per row again.

-   "Space" is how many words were needed altogether, including the top
    > hash table and the bucket hash tables.

-   "Overhead" is how many words were needed, including the top hash
    > table and the unused spots in the bucket hash tables. It's always
    > N less than Space.

-   Run time is in milliseconds.

-   Notice how the Average Space (relative to n) is decreasing. It's
    > possible that it's converging to 1.5n, because thats the minimum
    > number of words needed.

-   [[Source
    > here]{.underline}](https://github.com/Verdagon/TetrisTables/blob/master/src/TetrisTableGenerator.scala)

The space overhead is great now, but we the runtime just shot up; it's
now quadratic instead of linear!

The next section explains how we can bring that quadratic runtime down
to linear.

### Bucket Groups

Let's say we have these buckets:

-   Bucket 0 -\> \[null, G, null, null, null, C\]

-   Bucket 1 -\> \[\]

-   Bucket 2 -\> \[\]

-   Bucket 3 -\> \[F, null, D, null, J\]

-   Bucket 4 -\> \[\]

-   Bucket 5 -\> \[P\]

-   Bucket 6 -\> \[\]

-   Bucket 7 -\> \[B\]

-   Bucket 8 -\> \[H, null, I, K\]

-   Bucket 9 -\> \[M, null, null, null, N\]

-   Bucket 10 -\> \[\]

-   Bucket 11 -\> \[O, A\]

B is the number of buckets, 12 in this case.

We first form "bucket groups", of a constant number ("G") of buckets. G
has to be constant, for reasons discussed later. This means we end up
with B / G bucket groups, which is 12 / 4, which is 3, so we have 3
bucket groups.

Buckets 0-3 would be in the first bucket group, buckets 4-7 are the
second bucket group, and buckets 8-11 are the third bucket group.

Each bucket group uses linear tetris to produce a "Bucket Group Combined
Table". For example,

-   The first bucket group, buckets 0-3, are combined to form \[F, G, D,
    > null, J, C\].

-   The second bucket group, buckets 4-7, are combined to form \[P, B\]

-   The third bucket group, buckets 8-11, are combined to form \[M, O,
    > A, H, N, I, K\]

Next, we just concatenate the Bucket Group Combined Tables together. In
this case,

\[F, G, D, null, J, C\], \[P, B\], \[M, O, A, H, N, I, K\]

concatenate together to form the final Giant Combined Seconday Array,
which looks like:

\[F, G, D, null, J, C, P, B, M, O, A, H, N, I, K\].

Notes about the above:

-   We form a *variable number of groups of a constant size*, rather
    > than a *constant number of groups of variable size*. In other
    > words, G has to be constant. This is important because linear
    > tetris is an O(G\^2) algorithm, and if we keep G constant, then
    > the linear tetris becomes constant (at least, on average).

-   In the example above, B (12) divided into G (4) evenly, but that
    > doesn't have to be the case; the last bucket group can have less
    > buckets in it.

-   Note that we only concatenate the Bucket Group Combined Tables
    > together, we don't try to linear tetris them together.

This uses average linear space, and average linear run time, but there
are still some optimizations we can make.

### Optimizations

#### Distributing Buckets Evenly Into Groups

Notice how above, we chose that:

-   buckets 0-3 would be in the first bucket group,

-   buckets 4-7 are the second bucket group, and

-   buckets 8-11 are the third bucket group.

This was completely arbitrary, and we can do it more intelligently.

Instead, we're going to try to distribute the buckets among the bucket
groups such that each bucket group has an equal number of large buckets,
and an equal number of small buckets.

A bucket is larger than another bucket if its "span" is greater. The
span is the distance (inclusive) between the first full spot and the
last full spot. For example:

-   \[17, null, null, null, 400\]'s span is 5.

-   \[15, 16, null, 31\]'s span is 4.

-   \[4\]'s span is 1.

If two buckets have the same span, then we count the number of elements
inside them to break the tie. So, \[15, 17, 20\] is "larger" than \[16,
null, 23\] even though they have the same span.

We sort[^1] all of our buckets by the above ordering, we then distribute
them into the bucket groups round-robin style.

For example, given these buckets:

-   Bucket 0 -\> \[null, G, null, null, null, C\]

-   Bucket 1 -\> \[\]

-   Bucket 2 -\> \[\]

-   Bucket 3 -\> \[F, null, D, null, J\]

-   Bucket 4 -\> \[\]

-   Bucket 5 -\> \[P\]

-   Bucket 6 -\> \[\]

-   Bucket 7 -\> \[B\]

-   Bucket 8 -\> \[H, null, I, K\]

-   Bucket 9 -\> \[M, null, null, null, N\]

-   Bucket 10 -\> \[\]

-   Bucket 11 -\> \[O, A\]

This would be the correct ordering:

-   Bucket 0 -\> \[null, G, null, null, null, C\]

-   Bucket 9 -\> \[M, null, null, null, N\]

-   Bucket 3 -\> \[F, null, D, null, J\]

-   Bucket 8 -\> \[H, null, I, K\]

-   Bucket 11 -\> \[O, A\]

-   Bucket 5 -\> \[P\]

-   Bucket 7 -\> \[B\]

-   Bucket 1 -\> \[\]

-   Bucket 2 -\> \[\]

-   Bucket 4 -\> \[\]

-   Bucket 6 -\> \[\]

-   Bucket 10 -\> \[\]

So, we distribute them across the groups:

-   Bucket 0 goes into bucket group **0**

-   Bucket 9 goes into bucket group **1**

-   Bucket 3 goes into bucket group **2**

-   Bucket 8 goes into bucket group **0**

-   Bucket 11 goes into bucket group **1**

-   Bucket 5 goes into bucket group **2**

-   Bucket 7 goes into bucket group **0**

-   Bucket 1 goes into bucket group **1**

-   Bucket 2 goes into bucket group **2**

-   Bucket 4 goes into bucket group **0**

-   Bucket 6 goes into bucket group **1**

-   Bucket 10 goes into bucket group **2**

So here are the buckets groups:

-   Group 0: Buckets 0, 8, 7, 4: \[null, G, null, null, null, C\], \[H,
    > null, I, K\], \[B\], \[\]

-   Group 1: Buckets 9, 11, 1, 6: \[M, null, null, null, N\], \[O, A\],
    > \[\], \[\]

-   Group 2: Buckets 3, 5, 2, 10: \[F, null, D, null, J\], \[P\], \[\],
    > \[\]

Notice how it's a much more uniform distribution of larger and smaller
groups. In practice, this makes for much more compact tables.

#### Linear Tetris on Large Buckets First

In the previous section, we described how to order the buckets from
largest to smallest. This should also be the order in which we use
linear tetris within a bucket group.

This helps because the smaller buckets can fit in anywhere, so we save
them for the end, to fill in the gaps left by the larger buckets.

#### Choose the Best Number of Buckets

Above, we said that there has to be N/4 buckets. This isn't arbitrary.

First of all, it has to be linear with the number of elements. In other
words, it has to be N, or N/2, or N/4, or even 1.7N. This is so we end
up with an average constant number of elements per bucket. For example,
with N/4, we have on average 4 elements per bucket. It's important to
keep this number down, because the size of the bucket grows
exponentially with the number of elements in the bucket. If instead of
something like N/4 we just decided that there would be, for example, 256
buckets, then each bucket would have N/256 elements, which means its
size would grow exponentially as N got larger.

As for the choice between N, N/2, N/4, etc. buckets, we choose N/4
because it makes for the smallest overall space usage, according to
[[this
experiment]{.underline}](https://docs.google.com/spreadsheets/d/19JTIUbuJLbEmoi-trKx6KbXNNotmHe6Gu2qhRTdiDtk/edit?usp=sharing).

In that experiment, we used 1000 values, and tried the algorithm with:

-   15 buckets, which caused a lot of space overhead (4704.2 pointers on
    > average).

-   25 buckets, which caused less space overhead (3410.3 pointers on
    > average).

-   35 buckets, which caused even less (2699.2 pointers on average).

-   and so on, all the way to 2000 buckets.

The minimum was around 250 buckets, which used 1350 pointers of overhead
on average.

#### Mulligans

We could get an extremely unlucky data set, for example: 1000 values,
which are all multiples of 250, then they'll all land in the same bucket
(in this case, since there are 1000/4 = 250 buckets, all the values
would land in bucket 0).

In this case, we restart with 251 buckets and try again. We keep
incrementing and retrying until every single bucket has less than N/4
elements. In all of the benchmarks done for this document, this has
never happened, probably because we used random numbers, rather than
malicious ones.

### Performance

This table shows how well Tetris Hashing performs (including the above
optimizations):

+----+--------------+-----------+-------------+----------+------------+
| ** | **Average**  | **Worst** | **Average** | *        | *          |
| \# |              |           |             | *Worst** | *Average** |
| I  | **Space**    | **Space** | *           |          |            |
| Ds |              |           | *Overhead** | **Ov     | **Run      |
| ** |              |           |             | erhead** | Time**     |
+====+==============+===========+=============+==========+============+
| ** | 1654.838     | 1715      | 654.838     | 715      | 20.847     |
| 10 | (1.655n)     | (1.715n)  | (0.655n)    | (0.715n) | (0.021n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 3305.909     | 3385      | 1305.909    | 1385     | 39.342     |
| 20 | (1.653n)     | (1.693n)  | (0.653n)    | (0.693n) | (0.020n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 4970.957     | 5067      | 1970.957    | 2067     | 59.296     |
| 30 | (1.657n)     | (1.689n)  | (0.657n)    | (0.689n) | (0.020n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 6617.063     | 6700      | 2617.063    | 2700     | 74.866     |
| 40 | (1.654n)     | (1.675n)  | (0.654n)    | (0.675n) | (0.019n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 8280.896     | 8377      | 3280.896    | 3377     | 98.946     |
| 50 | (1.656n)     | (1.675n)  | (0.656n)    | (0.675n) | (0.020n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 9909.285     | 10013     | 3909.285    | 4013     | 127.291    |
| 60 | (1.652n)     | (1.669n)  | (0.652n)    | (0.669n) | (0.021n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 11619.151    | 11734     | 4619.151    | 4734     | 143.344    |
| 70 | (1.660n)     | (1.676n)  | (0.660n)    | (0.676n) | (0.020n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 13234.858    | 13359     | 5234.858    | 5359     | 162.761    |
| 80 | (1.654n)     | (1.670n)  | (0.654n)    | (0.670n) | (0.020n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| ** | 14908.243    | 15035     | 5908.243    | 6035     | 190.367    |
| 90 | (1.656n)     | (1.671n)  | (0.656n)    | (0.671n) | (0.021n)   |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+
| *  | 16554.960    | 16696     | 6554.960    | 6696     | 200.949    |
| *1 | (1.655n)     | (1.670n)  | (0.655n)    | (0.670n) | (0.020n)   |
| 00 |              |           |             |          |            |
| 00 |              |           |             |          |            |
| ** |              |           |             |          |            |
+----+--------------+-----------+-------------+----------+------------+

Notes about the table:

-   1000 trials per row.

-   "Space" is how many words were needed altogether, including the top
    > hash table and the bucket hash tables.

-   "Overhead" is how many words were needed, including the top hash
    > table and the unused spots in the bucket hash tables. It's always
    > N less than Space.

-   Run time is in milliseconds.

-   [[Source
    > here]{.underline}](https://github.com/Verdagon/TetrisTables/blob/master/src/GroupedTetrisTableGenerator.scala)

As we can see, we've reached our three goals: we have linear average
space overhead, linear average run time, and O(1) lookup time.

Note that we're only certain that the algorithm has linear **average**
runtime, and linear **average** space overhead, not linear worst case
overhead or run time. It's unknown whether Tetris Hashing is linear in
the worst case.

## Future Work

Coming soon, in the next few months:

-   This method requires on average two cache misses per lookup (one
    > cache miss for the top table, one cache miss for the secondary
    > table), which could make performance suffer (thanks for pointing
    > this out, Fabian Viger!). There can be a version of this which
    > splits the entire set along page boundaries; inside every page
    > there would be a tetris table. This would introduce one more load
    > and one more & or % operation, but reduce the number of cache
    > misses to 1.

-   This method requires modulus, which is actually one of the more
    > expensive operations (thanks Jan Wassenberg for pointing this
    > out!), so we can explore having a version of this which uses &,
    > but assumes a uniform distribution of input.

-   Benchmark this against Cuckoo Hashing, since it also has O(1) lookup
    > time (thanks for finding this, Milo Sredkov!)

-   Benchmark this against gperf and cmph.

[^1]: Bucket sort works nicely for this; it uses linear time.
