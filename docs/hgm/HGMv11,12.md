TL;DR V11: We can put all locals into a struct, and then put that struct
into a special linked-stack heap. We have the uni reference to that
struct, and it\'s guaranteed to be alive.

TL;DR V12: Only mutables go there, and callees can use callers stack
frames.

We need a way to dereference a generational reference correctly, even if
it\'s on the stack. We need to increment the generation every time we
change the shape of what\'s at a memory location. Otherwise, some
reference to the past object will pass its generation check and access
memory in an unsafe way. However, we need to change the shape whenever
we go down a different branch which has different inlined things, or
when we go down a different override for an interface method. But,
incrementing a generation can be expensive, because we have to retire a
block if we hit 0xFF.

The naive approach: have parallel stacks for each possible block size.
The downside is that they will take up a bit of memory, right now 40kb.
If we keep a bunch of them on one core, then they can share their free
lists and overhead.

Another approach: have big 256b stack frames, and hoist all mutables
like C would, regardless of what block they came from. And perhaps we
can be more conservative with the space we give to interface calls,
maybe 64b.

An interesting approach: We combine blocks, with some clever tricks. For
example:

> fn stringify(i int) str {
>
> s2 = str(i);
>
> ret s2;
>
> }
>
> fn main() {
>
> x = stdinReadInt();
>
> if (x \< 20) {
>
> while (x \< 10) {
>
> s1 = stringify(x);
>
> n = len(s1);
>
> b = n \< 3;
>
> printf(b);
>
> mut x = x + 1;
>
> }
>
> } else {
>
> z = x \* 2;
>
> q = z / 2;
>
> println(q);
>
> }
>
> }

All of main can be summed up like this:

-   int: x

-   str: s1 and s2 (yes, s2 from the callee!)

-   int: n and z (yes, even though theyre from different blocks)

-   bool: b

-   int: q

see how we merged the stack frames of alternate branches of the if
statements. And see also how we brought in a callee. We could do some
pretty smart merging like this, to cut down on the number of stack
\"superframes\" we need.

At any given if-statement, we have the choice of whether to fit both
branches (merged) into the parent block, or to request a new block from
the linked stack.

To factor into that decision, we would look at how much room the new
block needs, and how much is left in the parent block. We could say that
the stack frame is by default 128 bytes, and use-both-merged branches
until thats exhausted, then perhaps request a new one.

We could also have a 64b stack frame, just in case. We\'ll see what
performance needs.

We\'ll have a local (on the main stack) which basically serves as %rbp,
and points at the current block. It\'ll probably be in a register very
often.

## Mutables Only

We could put only mutables onto this stack, since theyre the only things
that actually need generations. All immutable things can be on the
regular stack.

This might mean we can change our default stack frame size from 128
bytes to be higher or lower, and perhaps we can even have two (say, 64b
and 256b?). Who knows!

## Reserve Multiple

If we have a loop with an if-statement inside, we can actually reserve
*multiple* blocks up-front.

> fn doThings(myList &List\<Spaceship\>) {
>
> each x in myList {
>
> if x \< 10 {
>
> doSomeThing();
>
> } else {
>
> doOtherThing();
>
> }
>
> }
>
> }

doThings can actually allocate an entire block for itself (optional), an
entire block for the then branch (with doSomeThing), and an entire block
for the else branch (with doOtherThing).

It can reuse the doSomeThing block for all iterations that go down that
branch. And it could reuse the doOtherThing block for all iterations
that go down *that* branch.

That way, it doesn\'t have to keep pulling them from the allocator every
iteration.

## Green Threads

Note how our reference-able data is in this linked stack off to the
side, and nothing refers to our regular stack.

This could enable really nice green threads. Everything\'s already in a
linked stack! Or we could have things in the main stack be copied into
the linked stack instead, either on suspend or on call or something.

Yeah, this can work. An executor can have a bunch of green threads. An
executor can be owned by only 1 core, so they can share their memory.

## Compaction

This could also enable compaction, because we defined our own structs.
We would just trace from the linked stack through all reachable objects,
come up with a list, and compact everything. This would be a last
resort, hopefully.
