every object has an atomic ref count.

use optimizations to figure out when we can skip incrementing it (such
as when we have as a parameter the object we got it from)

most thread just atomically increment and decrement the ref count.

performance critical threads push the ref counting to another thread by
just writing to the next spot in the linked queue.

also with the linked queue is the \"next\" pointer but there are
actually TWO of these, one for the hero and one for the sidekick.

first, the hero writes to his next pointer.

then, the hero increments his next pointer.

on the sidekick side, the sidekick listens for its next pointer. when it
detects a nonzero there, it starts reading until it hits a zero.

the message structure will be:

when constructing a regular struct-ish immutable thing, we just put the
pointer, or\'d with 000. the next word will be the type ID.

when constructing an immutable with a vptr, we put the pointer, or\'d
with 001.

when adding a reference to an immutable, we put the pointer, or\'d with
010.

when removing a reference to an immutable, we put the pointer, or\'d
with 011.

when constructing a regular struct-ish mutable, we put the pointer,
or\'d with 100.

when constructing a mutable with a vptr, we put the pointer, or\'d with
101.

when destructing a mutable, we put the pointer, or\'d with 110.

when allocating an entire new page, we put the pointer to it, or\'d with
111.

note: we\'re using up the entire 8 bits we have here. if we ever need
another one for some reason, we can get rid of 110 and instead just send
another 100 or 101, and have the sidekick say \"we already have
something at this address, this must mean the hero\'s getting rid of
it\".

the hero thread must be careful to do a memory barrier every time it
grabs a new page. the sidekick thread should NOT read from a page until
it knows a new page exists (that way, it knows this page has been memory
barrier\'d). we might even just unlock that page\'s mutex by sending a
signal, that would provide a nice way to wake it up. one nice
alternative is to do this every N pages.

the hero thread also has a linked page queue of memory that it can
allocate from. it\'s responsible for adding things to it.

at the top of every page is a counter of how many objects are in it.
it\'s up to the sidekick to maintain this.

at the end of every page is a pointer saying what the next one is. this
starts as 0. when the hero runs out of memory in this page, it checks
the from-sidekick incoming empty page queue and uses one from there. if
there are none, it calls malloc to get a new page.

the hero also has a different queue which is used only for messages.
when it runs out of space, it checks the incoming empty page queue and
grabs one of those, or it mallocs a new one.

the sidekick receives the object queue, waits until it has a next
pointer, and after a while, tries to move immutables out of it, or into
it, depending on if there are any mutables in it after a certain number
of seconds (or generations? dunno). it might fail to move immutables out
of it, in which case, it will move immutables into it again.

the sidekick receives the message queue, waits until it gets a signal
from the hero thread, and then eats messages from it until there are no
more, returns empty pages to the queue so they can be reused, and then
waits on the condition again.

the sidekick should definitely be on its own thread on its own core, so
as to not block the main thread. or set priorities or something.

\...we can implement this with c++, not that we want to. lets make this
a separate mode in vale. lets also implement all the optimizations that
swift has, so we can compare how much better vale is than swift.

when adding an immutable thing in the hero thread, must first write its
type ID into the object buffer.

it should be considered to have a reference count of 1 by default.

decrement immutable: pointer \| 00

increment immutable: pointer \| 01

received a message from another thread: message pointer \| 10

sending a message to another thread: message pointer \| 11

destroy mutable: pointer \| 100

we might want to do a condition signal on that last one. also, we\'d
want a pointer to where we are in the message queue, on the old object
page.

hero sends a condition when we run out of room on the message queue, or
run out of room on the object queue.

message page layout:

end:

\- endpointer in the object page

\- next page pointer

object page layout:

beginning:

\- 8 bytes of size

end:

\- endpointer in the message page

\- next page pointer

we dont really have loops, but we will have tail call optimization.

if we have a function like:

void doThings(a: MyStruct, b: OtherStruct) {

let c = b;

if (a.numWheels \< 4) {

let d = a.withNumWheels(8);

print(d.numWheels);

} else {

print(c.name);

}

}

before the branch is 1 message

that first branch causes like 5 messages

that second branch causes like 2 messages

after the branch is 1 message

so, we can take the max of those, meaning we need around 7 messages to
execute this function. we include that in the function header, and the
caller can deal with the message allocating.

lets say we made that recursive:

void doThings(a: MyStruct, b: OtherStruct) {

let c = b;

if (a.numWheels \< 4) {

let d = a.withNumWheels(8);

print(d.numWheels);

doThings(d);

} else {

print(c.name);

}

}

we\'d probably say that it needs 6 things. then, just before calling
itself, it\'ll look at itself and do a check before it.

now, what happens if we do one unroll?

the entry is still 1 message

the false path is still 1+2+1 = 4 messages.

the true-false path is 1+5+1+2 = 8 messages in entry, then 2 messages on
exit.

the true-true path is 1+5+1+5 = 12 messages in entry, then 2 messages on
exit.

so, we\'d say that we need 12 messages. huh, thats double what we had
before.

maybe we\'d want to unroll until we get to\... 64? 32? not sure. are
there any other things we want to unroll on?

we probably want to have unrolling as a compiler parameter, and then
just have our message sendy thing do the most optimal thing after
unrolling. in other words, lets do our message management in the stage
after the unrolls.

if we have the counter on the outside, then we can have a separate one
per object per thread. if we can have separate counters per thread\...

if local:

would be NULL

if not:

would point to the central coordinator\'s block

central coordinator\'s block:

\- atomic reference counter

\- (optional) pointer to the central coordinator, if there are multiple.

\- prev and next of a circular list

when a central coordinator gets overwhelmed, we can spin up a different
central coordinator.

when a thread notices that a buffer is almost full, perhaps it can
create a new central coordinator for itself? then when it no longer
tracks any objects, maybe it can delete itself?

will be interesting to see all this done locklessly. will we need
conditions/signals? it would be terrible to have an infinite loop
somewhere.

i wonder if we can fire off a thread whenever we do an allocation or a
deallocation, to clean up stuff? seems like it could be hiccupy though.

if we throw something across a thread, and it\'s already international,
then we just add to its circular linked list. we might need to send that
as a message to the reaper thread. perhaps atomic increment at the same
time? can we skip that? maybe not.

when we decide we dont need something anymore, and it\'s international,
then we send a message to the international thread with the pointer to
the entry in its list. international thread will do the removing.

you know, there could be multiple international threads\... every object
would point to a random one.

actually, just put the type id before the object at all times. the hero
wouldnt have to send a message when he creates immutable objects then,
sidekick would just watch the object page list grow and see whats coming
into existence there.

the first pointer is always the vptr. when the hero creates a non-class,
it puts a vptr in there anyway.

the new set of messages:

when adding a reference to an immutable, we put the pointer, or\'d with
00.

when removing a reference to an immutable, we put the pointer, or\'d
with 01.

when destroying a mutable, we put the pointer, or\'d with 01. (same as
above)

when allocating an entire new page, we put the pointer to it, or\'d with
11.

just before we send the condition to wake up the sidekick, we should
tell them where the end of the object queue is right now. perhaps that
can be the second to last word in the message queue.

can we really combine those two blasts of un-lined-up data?

lets make hero threads more efficient. lets use cache friendliness,
minimize branching, and maybe even simd stuff.

from doc:

decrement immutable: pointer \| 00

increment immutable: pointer \| 01

received a message from another thread: message pointer \| 10

sending a message to another thread: message pointer \| 11

destroy mutable: pointer \| 100

assuming each message block is 4kb, or 512 pointers.

first, lets categorize by kind of message.

lets get 5 chunks of 512 pointers. in fact, lets reuse the current
buffer for the decrement messages. lets use something from the stack for
our increments. both are guaranteed to already be in cache =D. and lets
use the stack for the rest anyway.

int\*\* decrements = messages;

int\* increments\[512\];

void\* destroys\[512\];

void\* receives\[512\];

void\* sends\[512\];

void\* nextPointers\[5\] = { decrements, increments, receives, sends,
destroys };

int\* messagesSorted\[5\]\[256\]; // \[0\] is things to decrement, \[1\]
is things to increment, etc.

for (void\* messagePtr = messages; message \< messages + 512; message++)
{

void\* message = \*messagePtr;

int messageType = message & 0x7;

nextPointers\[messageType\] = message & 0xFFFFFF8;

nextPointers\[messageType\]++;

}

then, loop through the increments and decrements.

for (int\*\* decrement = decrements; decrement \< nextPointers\[0\];
decrement++) {

(\*decrement)\--; // assumes ref count is at the top of the object.

}

i dont think we can use SIMD for the above, because two of the pointers
might be pointing at the same thing. but theres no branching, which is
nice.

do the same for increments. i have no idea what sends and receives and
destroys do, but they will doubtlessly benefit from a bit less
branching.

improvement on the above decrementing\... we need to destroy a bunch of
immutables recursively. we could either loop through the decrements
array and see who are zero, or we can construct it as we go:

void\* trash;

void\*\* doomedRouter\[2\] = { &trash, decrement };

for (int\*\* decrement = decrements; decrement \< nextPointers\[0\];
decrement++) {

int refCount = \*decrement;

refCount\--;

\*decrement = refCount;

bool destroy = !refCount; // destroy=1 if ref count hit 0. destroy=0 if
want to keep alive.

\*doomedRouter\[destroy\] = decrement;

// bump doomedRouter\[destroy\] to the next pointer, IF destroy=1.

doomedRouter\[destroy\] += 1 \* destroy;

}

then, we have a nice list of things to destroy.

for (void\*\* toDestroy = decrements; toDestroy \< doomedRouter\[1\];
toDestroy++) {

// do whatever it is we want to do when we destroy immutable things.

}

you know, we\'ll never get two destroy messages for the same object.
whatever we do for destroy might benefit from a little SIMD action.

if we double the size of these buffers, we can keep on consuming
incoming pages until one of these buffers gets past halfway full. but,
this could delay some increments and decrements, which could be harmful,
delaying memory too much.

how do we handle cascading deletes? the sidekick thread can probably
just send pages of decrements at itself, just like hero threads do.

we'd probably categorize the destroyed immutables by how many members
they have, that they want to decrement.

for (void\*\* toDestroy = decrements; toDestroy \< doomedRouter\[1\];
toDestroy++) {

}
