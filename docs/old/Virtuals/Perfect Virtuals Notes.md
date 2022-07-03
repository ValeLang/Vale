cpu can do 4 instructions per cycle

14 to 25 cycles for a branch misprediction.

L1 cache: 3 cycles (4 on aristeia slide 18)

L2 cache: 12 cycles (11 on aristeia slide 18)

L3 cache: 38 cycles (39 on aristeia slide 18)

main mem: 195 cycles (107 on aristeia slide 18)

if we assume a load factor of .5, that means once it hits .5, it will
double in size. that means we\'re always around .25-.5 filled. lets
assume an average of .375.

\- if we have our branch predict that we found it, then it\'s a .375%
chance youll miss. a .375 youll miss again after that (so .375\^2 youll
miss twice).

\- if we have our branch predict that we didnt find it, then it\'s
pretty much 20 cycles.

\- if we have a primitive branch predictor that initially predicts we
find it, but after that predicts a repeat of history, then it\'ll be 30
cycles.

assuming it\'s going with the first.

assuming 20 cycles for a cache misprediction, then on average, the
branch mispredictions in linear probing will cost 7.82 cycles, see
http://www.wolframalpha.com/input/?i=.5\*1%2B21\*.375%5E2%2B41\*.375%5E3%2B61\*.375%5E4%2B81\*.375%5E5%2B101\*.375%5E6%2B121\*.375%5E7
so lets just say an even 8.

c++:

\- a cache miss for loading the object, to get the itable vptr

\- a cache miss for loading the itable vptr to get the thunk pointer

\- a cache miss for loading the thunk to call it

\- a cache miss for loading the actual function to call it

rust:

\- a cache miss for getting the pointer out of the itable

\- a cache miss to load the thunk function into memory to call it

\- a cache miss for the thunk to bring the actual function into memory
to call it

java:

\- a cache miss for loading the object to get the function table\'s
beginning cache line with the size

\- a cache miss to load the part of the table where we land

\- 8 cycles to do linear probing until we get the function pointer

\- a cache miss to load the function into memory to call it

perfect virtuals:

\- a cache miss to load the table header to get the size of the etable

\- a cache miss to load the part of the etable where we land and get the
bucket pointer and size

\- a cache miss to load the part of the bucket where we land and get the
itable pointer

\- a cache miss to load the part of the itable that has the method
pointer

\- a cache miss to load the function into memory and call it

hybrid rust/java:

\- a cache miss to load the table header to get the size of the etable

\- a cache miss to load the part of the etable where we land

\- 8 cycles to do linear probing until we get to the pointer to the
itable we want

\- a cache miss to load the part of the itable that has the method
pointer

\- a cache miss to load the function into memory and call it

so, we can convert a cache miss into 8 cycles of branch mispredictions.

but, keep in mind, we keep the itable around on the stack.

now, in a subcomponents world, where we hand around a pointer to the
parent object, and then delegate downward to a basic struct
implementation which is inline:

c++:

\- a cache miss for loading the object, to get the overall vptr

\- a cache miss for loading the overall vptr to get the thunk pointer

\- a cache miss for loading the thunk to call it

\- a cache miss for loading the actual function to call it

though keep in mind, if this is the first base class, then there\'s no
thunk, so just 3.

4 cache misses

rust:

(we already have a pointer to the itable, from the fat pointer)

\- a cache miss for getting the pointer out of the overall itable

\- a cache miss to load the thunk function into memory to call it

\- a cache miss for the thunk to bring the actual function into memory
to call it

\- a cache miss for calling the subcomponent\'s function

4 cache misses

java:

(impossible)

vale:

\- 5 cache misses to call my function

\- another cache miss to load the basic struct method into memory and
call it

6 cache misses

now, in a subcomponents world, where we hand around a pointer to the
parent object, and then delegate downward to a basic struct
implementation which is not inline:

c++:

\- a cache miss for loading the object, to get the overall vptr

\- a cache miss for loading the overall vptr to get the thunk pointer

\- a cache miss for loading the thunk to call it

\- MAYBE a cache miss for the thunk loading the pointer to the
subcomponent.

\- a cache miss for loading the actual function to call it

4.5 cache misses

rust:

(we already have a pointer to the itable, from the fat pointer)

\- a cache miss for getting the pointer out of the overall itable

\- a cache miss to load the thunk function into memory to call it

\- a cache miss for the thunk to bring the actual function into memory
to call it

\- PROBABLY a cache miss for the thunk loading the pointer to the
subcomponent. fat pointers make this a bit more likely.

\- a cache miss for calling the subcomponent\'s function

4.7 cache misses

java:

\- a cache miss for loading the object to get the function table\'s
beginning cache line with the size

\- a cache miss to load the part of the table where we land

\- 8 cycles to do linear probing until we get the function pointer

\- a cache miss to load the function into memory to call it

\- MAYBE a cache miss to load the pointer of the subcomponent from the
object

\- a cache miss to load the subcomponent\'s function

4.5 cache misses, 8 cycles to misprediction

now, in a subcomponents world, where we hand around a pointer to the
parent object, and then delegate downward to an interface subcomponent
(not inline):

c++:

\- a cache miss for loading the object, to get the overall vptr

\- a cache miss for loading the overall vptr to get the thunk pointer

\- a cache miss for loading the thunk to call it

\- MAYBE a cache miss for loading the pointer to the subcomponent.

\- a cache miss for loading the subcomponent object, to later get its
vptr

\- a cache miss for loading the subcomponent\'s vptr

\- a cache miss for loading the subcomponent method into memory to call
it

6.5 cache misses

rust:

(we already have a pointer to the itable, from the fat pointer)

\- a cache miss for getting the pointer out of the overall itable

\- a cache miss to load the thunk function into memory to call it

\- a cache miss for the thunk to bring the actual function into memory
to call it

\- PROBABLY a cache miss to load the pointer of the subcomponent from
the object. fat pointers make this a bit more likely.

\- a cache miss for loading the part of the itable into memory to get
the method pointer

\- a cache miss for calling the subcomponent\'s method

5.7 cache misses

java:

\- a cache miss for loading the object to get the function table\'s
beginning cache line with the size

\- a cache miss to load the part of the table where we land

\- 8 cycles to do linear probing until we get the function pointer

\- a cache miss to load the function into memory to call it

\- MAYBE a cache miss to load the pointer of the subcomponent from the
object

\- a cache miss to load the object to get the function table\'s
beginning cache line with the size

\- a cache miss to load the part of the table where we land

\- 8 cycles to do linear probing until we get the function pointer

\- a cache miss to load the function into memory to call it

6.5 cache misses, 16 cycles to branch misprediction

vale:

10 cache misses

hybrid:

8 cache misses, 16 cycles to branch misprediction

so, vale is doing really good with memory usage. not so good with cache
misses. it\'s not going to be the fastest thing ever.
