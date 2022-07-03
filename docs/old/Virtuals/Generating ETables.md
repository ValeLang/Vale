**Background**

Perfect Virtuals require some hashtable\<interfaceID, ITable\*\>, ones
that are O(1). Regular hash maps won't do because they're O(n) and
involve expensive branching. So, we need an algorithm to generate a
perfect hash table, one that guarantees no collisions.

Generating a perfect hash table is theoretically very easy. Lets say we
have N IDs we want to put into a hash table. We could:

-   Start with a size N array.

-   Take all the IDs, modulus them by the array's size, and see if
    > there's any collisions.

-   If there were no collisions, we're done.

-   If there were collisions, expand the array's size by 1, and try
    > again.

However, this method's efficiency leaves something to be desired. For
example, if we have 10 IDs, the average table size is between 25 and 26.

Note I said average; the algorithm was run 1000 times, on 10 random IDs
each time, and possible table sizes ranged anywhere from 10 to 49.

It gets worse if there are 20 IDs; on average we need a table of size 72
to perfectly hash 20 IDs, and worst case we needed a table of size 123.

  ------------------------------------------------------------------------
  **\# IDs**  **Worst ETable Size**        **Average ETable Size**
  ----------- ---------------------------- -------------------------------
  10          49                           25.411

  20          123                          72.079

  30          221                          134.778

  40          342                          214.435

  50          467                          306.176

  60          660                          410.362

  70          790                          541.284

  80          1029                         668.795

  90          1141                         807.979

  100         1455                         970.438

  110         1617                         1147.299

  120         1933                         1311.19
  ------------------------------------------------------------------------

The big question is: are there any other ways to make a perfect hash
table, but that don't use up so much space? This doc explores ideas to
that end.

The main two avenues we have are:

### Alternative Hash Functions

#### Adding an XOR

the range of the values doesnt seem to matter

100 different random numbers:

let multiplier = randomConstantA;

let xorrer = randomConstantB;

num elements 10 worst 19 average 13.707

num elements 20 worst 53 average 40.275

num elements 30 worst 103 average 79.035

num elements 40 worst 177 average 127.813

num elements 50 worst 249 average 187.956

num elements 60 worst 329 average 256.974

num elements 70 worst 443 average 336.409

num elements 80 worst 535 average 422.562

num elements 90 worst 665 average 519.437

num elements 100 worst 801 average 626.591

num elements 110 worst 965 average 739.778

num elements 120 worst 1087 average 866.772

mult xor mod A:

let multiplier = randomConstantA;

let xorrer = randomConstantB;

num elements 10 worst 11 average 10.915

num elements 20 worst 31 average 23.483

num elements 30 worst 59 average 43.018

num elements 40 worst 95 average 69.511

num elements 50 worst 135 average 100.645

num elements 60 worst 185 average 137.48

num elements 70 worst 237 average 179.156

num elements 80 worst 301 average 227.335

num elements 90 worst 369 average 277.539

num elements 100 worst 433 average 333.517

num elements 110 worst 511 average 394.312

num elements 120 worst 581 average 457.521

mult xor mod B:

let multiplier = randomConstantA;

let xorrer = trial \< values.length ? randomValue : randomConstantB;

((value \* multiplier) \^ xorrer) % tableSize

num elements 10 worst 11 average 10.934

num elements 20 worst 31 average 23.527

num elements 30 worst 59 average 43.092

num elements 40 worst 93 average 69.47

num elements 50 worst 131 average 100.933

num elements 60 worst 187 average 137.815

num elements 70 worst 241 average 178.644

num elements 80 worst 303 average 226.642

num elements 90 worst 365 average 278.245

num elements 100 worst 431 average 331.957

num elements 110 worst 511 average 394.858

num elements 120 worst 627 average 462.934

mult xor mod C:

let multiplier = randomConstantA;

let xorrer = trial \< values.length ? randomValue \* multiplier :
randomConstantB;

num elements 10 worst 11 average 10.92

num elements 20 worst 33 average 23.76

num elements 30 worst 61 average 43.125

num elements 40 worst 93 average 70.147

num elements 50 worst 135 average 101.952

num elements 60 worst 185 average 139.214

num elements 70 worst 237 average 181.374

num elements 80 worst 313 average 228.391

num elements 90 worst 367 average 280.401

num elements 100 worst 431 average 335.3

num elements 110 worst 511 average 399.992

num elements 120 worst 607 average 464.963

so those complications make it \*worse\*.

100 different random numbers, and random 0-31 rotations:

num elements 10 worst 11 average 10.01

num elements 20 worst 29 average 22.317

num elements 30 worst 55 average 40.399

num elements 40 worst 86 average 65.715

num elements 50 worst 122 average 95.125

num elements 60 worst 170 average 129.678

num elements 70 worst 218 average 169.593

num elements 80 worst 277 average 213.365

num elements 90 worst 345 average 262.17

num elements 100 worst 404 average 315.863

num elements 110 worst 474 average 373.751

num elements 120 worst 554 average 436.568

thats the best so far. Up to 100, it uses 32 bytes per entry.

hmm... we \*might\* be able to eventually reuse that space...

if the table contains a pointer to the edge, and the first thing in the
edge is a pointer to the interface info, then that's unique. no other
pointer can ever point there.

so, we can put any other pointers we want into the gaps there, as long
as they only contain non-edge pointers to things whose first members
arent interface infos.

perhaps some sort of linked list can use up that space? linked lists can
have their 'next' pointer in there as a first member. what kind of stuff
would we want in a circular linked list like that?

aha! member variables, for reflection! those can live in that space.
every member has a name and a type. the type wont work because its an
interface pointer, but the name will work.

we'd have to have an index be somewhere. since its length isnt constant,
it will have to live somewhere outside of the sinfo anyway.

NEVERMIND it contains an interface pointer.

hmm... the smaller these reflection entries are, the more reason to just
have a nice contiguous table for it. the larger they are, the less
likely they will fit in the gaps. if only there was something variable
length.

what constant structures of size 2 or 3 have pointers that arent type
pointers?, preferably linked lists?

method pointers, i suppose...

in the end, this isnt really a problem... we can just compile it to use
a 2-level hash function if they really have that many edges. thats
linear space, still constant time... unfortunate though.

what about something that only ever points to raw structs, or raw
functions?

(function() {

function rot(x, numBits) {

return ((x \<\< numBits) \| (x \>\>\> (32-numBits))) & (0xFFFFFFFF)

}

function makeRandomValues(numValues) {

let values = new Set();

while (values.size \< numValues) {

values.add(Math.floor(Math.random() \* 1073741824))

}

return Array.from(values);

}

function getSmallishTable(values) {

for (let tableSize = values.length; tableSize \< values.length \*
values.length; tableSize++) {

for (let trial = 0; trial \< 100; trial++) {

let randomValue = values\[Math.floor(Math.random() \* values.length)\];

let randomConstantA = Math.floor(Math.random() \* 1073741824);

let randomConstantB = Math.floor(Math.random() \* 1073741824);

let randomShifter = Math.floor(Math.random() \* 32);

let shifter = randomShifter;

let xorrer = randomConstantB;

let table = new Map();

for (let i = 0; i \< values.length; i++) {

let value = values\[i\];

let key = rot(value \^ xorrer, shifter) % tableSize;

table.set(key, value);

}

if (table.size == values.length) {

return tableSize;

}

}

}

throw \"no!\"

}

function getBiggestAndAverage(numValues) {

let worstSmallish = 0;

let averageSmallish = 0;

let numAttempts = 1000;

for (let attempt = 0; attempt \< numAttempts; attempt++) {

let values = makeRandomValues(numValues);

let smallishTableSize = getSmallishTable(values);

worstSmallish = Math.max(worstSmallish, smallishTableSize);

averageSmallish += smallishTableSize;

}

return \"num elements \" + numValues + \" worst \" + worstSmallish + \"
average \" + (averageSmallish / numAttempts);

}

console.log(getBiggestAndAverage(10));

console.log(getBiggestAndAverage(20));

console.log(getBiggestAndAverage(30));

console.log(getBiggestAndAverage(40));

console.log(getBiggestAndAverage(50));

console.log(getBiggestAndAverage(60));

console.log(getBiggestAndAverage(70));

console.log(getBiggestAndAverage(80));

console.log(getBiggestAndAverage(90));

console.log(getBiggestAndAverage(100));

console.log(getBiggestAndAverage(110));

console.log(getBiggestAndAverage(120));

})()

when using CHDish, with just a random xor at the second level:

num elements 10 worst 24 average 22.02

num elements 100 worst 253 average 239.77

num elements 1000 worst 2575 average 2536.13

num elements 10000 worst 26014 average 25840.75

so, about 2.5x

when using CHDish, with a random shifter and xor at each level:

num elements 10 worst 20 average 20

num elements 100 worst 205 average 202.97

num elements 1000 worst 2092 average 2082.35

num elements 10000 worst 21071 average 21021.32

so, about 2.1x

they both seem to be slightly quadratic. definitely not as much as the
one-table approach though...

(function() {

function rot(x, numBits) {

return ((x \<\< numBits) \| (x \>\>\> (32-numBits))) & (0xFFFFFFFF)

}

function makeRandomValues(numValues) {

let values = new Set();

while (values.size \< numValues) {

values.add(Math.floor(Math.random() \* 1073741824))

}

return Array.from(values);

}

function doCHD(values) {

let bestNumSpaces = 13333337;

for (let tryI = 0; tryI \< 100; tryI++) {

let randomConstantA = Math.floor(Math.random() \* 1073741824);

let randomConstantB = Math.floor(Math.random() \* 1073741824);

let randomShifterA = Math.floor(Math.random() \* 32);

let randomShifterB = Math.floor(Math.random() \* 32);

let buckets = {};

for (let value of values) {

let hashA = rot(value \^ randomConstantA, randomShifterA) %
values.length;

buckets\[hashA\] = buckets\[hashA\] \|\| \[\];

buckets\[hashA\].push(value);

}

let numSpacesUsed = 0;

for (let bucketKey in buckets) {

let bucketValues = buckets\[bucketKey\];

for (let bucketCapacity = bucketValues.length; ; bucketCapacity++) {

let bucket = \[\];

let goodBucket = true;

for (let i = 0; i \< bucketCapacity; i++)

bucket.push(false);

for (let bucketValue of bucketValues) {

let hashB = rot(bucketValue \^ randomConstantB, randomShifterB) %
bucketCapacity;

if (bucket\[hashB\])

goodBucket = false;

else

bucket\[hashB\] = true;

}

if (goodBucket) {

numSpacesUsed += 3 + bucketCapacity; // 3 for the size, xor, and shifter

break;

}

}

}

if (bestNumSpaces == null \|\| numSpacesUsed \< bestNumSpaces) {

bestNumSpaces = numSpacesUsed;

}

}

return values.length + bestNumSpaces;

}

function getBiggestAndAverage(numValues) {

let worstSmallish = 0;

let averageSmallish = 0;

let numAttempts = 100;

for (let attempt = 0; attempt \< numAttempts; attempt++) {

let values = makeRandomValues(numValues);

let bestNumSpaces = doCHD(values);

worstSmallish = Math.max(worstSmallish, bestNumSpaces);

averageSmallish += bestNumSpaces;

}

return \"num elements \" + numValues + \" worst \" + worstSmallish + \"
average \" + (averageSmallish / numAttempts);

}

console.log(getBiggestAndAverage(10));

console.log(getBiggestAndAverage(100));

console.log(getBiggestAndAverage(1000));

console.log(getBiggestAndAverage(10000));

})()

crazy idea, what if we start with the classes that have the most, and
then literally determine the interface IDs from that, so that those
classes hash more nicely? if we keep randomly jiggling the interface IDs
that collide until we get a nice hash, that could be siiick. nice
benefit of determining your own interface IDs.

keep doing this:

-   generate a new xorrer and shifter perhaps 10 times and use the one
    > with the least collisions

-   until it doesnt help anymore:

    -   three times, change all known colliders. keep the best result.

    -   see if the best result decreased the total number of colliders.
        > if so, continue. if not, break.

-   alternatively:

    -   start with the biggest table, and change its colliders until
        > none collide. lock down everything in that table. move to the
        > table with the most locked numbers (maybe factor in how much
        > breathing room too?), and change the unlocked values. keep
        > going until we're done. if a table gets locked into badness,
        > then mark it for future expansion.

-   alternatively:

    -   start with the table most under pressure, and change its
        > colliders until none collide. we dont need to randomize this,
        > we can just make it happen by reversing the hash formula.
        > (perhaps we can try putting it in various multiples of
        > tablesize, and pick the one that causes the least collisions
        > in other tables?) lock down everything in that table. move to
        > the table next most under pressure, change its xorrer and
        > shifter til the lockeds dont collide, and change the unlocked
        > values. keep going until we're done. if a table gets locked
        > into badness, then mark it for future expansion.

        -   under pressure means breathing room; how likely we are to
            > get a good hash if we shuffle all colliders. factors: size
            > of table, number of free values, number of locked values.
            > figure out some sort of formula?

        -   would be interesting to see this in action. i can see the
            > biggest table working out fine, but other tables not
            > faring so well. this gives the biggest table a massive
            > advantage. perhaps we can choose randomly, weighted by
            > pressure?

            -   perhaps instead we can lock down one value in this
                > table, and then lock down a value in the next table,
                > and keep going like that? we dont have to do all
                > values in one table at once.

        -   perhaps there's a flavor of this algorithm that doesnt go
            > table to table, but rather value to value? keep changing
            > it until it hashes to a nice place in every table? \...i
            > dont think that would work.

        -   instead of increasing the table later, increase it
            > immediately, once. if that doesnt work, move on?

-   if we got to 0 collisions, exit success. else, continue.

-   for all tables that still have colliders, increase their size, and
    > start again.
