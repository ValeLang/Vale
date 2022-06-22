
There are a lot of existing approaches for concurrency, with plenty of benefits and drawbacks:

 * OO threads: Easy, but uses slow context switching, each thread wastes a lot of address space (8mb) and physical memory (~2kb avg).
 * Async/await: Compact, but has viral function coloring, can be difficult to use with recursion and virtual calls.
 * Zig's colorless async/await: Compact, easy, but doubles binary sizes, and can't work with recursion or through interfaces (not that Zig has interfaces).
 * Goroutines w/ stack copying: Compact, easy, but requires GC.
 * Goroutines w/ segmented stacks: Easy, but can have mysterious heap churning in loops.
 * Google's cooperative OS threads: Easy, but each thread wastes a lot of address space (8mb) and physical memory (~2kb avg).
 * Actors: Compact, but must program without a stack, similar to making a state machine like in days of yore.
 * Loom: Compact, easy, but requires GC. Also does a little more copying than goroutines. ([source](https://youtu.be/NV46KFV1m-4))


The leading contenders seem to be goroutines and Zig's colorless async/await. Is there a way to get goroutines' ease without its GC? And is there a way to get Zig's benefits but with virtual dispatch and recursion?


Hold my beer.


# Vale's Virtual Threads

## Start with Goroutines w/ Segmented Stacks

[See this article](https://blog.cloudflare.com/how-stacks-are-handled-in-go/) for more details, but TL;DR: A goroutine's stack is a linked list of 8kb chunks. At the beginning of every function, there's a "preamble" that checks if the function needs more stack space than available. If so, it allocates a new 8kb chunk onto the end of the linked list, and uses that for the new function call instead.

There are some problems though:

 * If we have a mostly-full 8kb chunk, and inside a loop we call a function, we'll be rapidly repeatedly allocating and deallocating the new 8kb chunk, causing some churn.
 * Doing this preamble check in every function is a bit extreme, a lot of performance lost.
 * It's not a zero-cost abstraction; we do this preamble check even if we're not doing concurrency.


## Use Free-Lists

To solve the first problem, we'll have a free-list of memory chunks which we can use for stack space. This is slightly easier in Vale than Go, because Vale doesn't have a GC to worry about, and Vale's virtual threads stay on one os thread so there's no complex locking going on to manage the free list.


## Merge the preambles

To solve the second problem, instead of having that preamble on every function, let's let the caller function reserve enough space for itself and its callees as well.

For example, if function A requires 112 bytes, and calls function B which requires 72 bytes, then function A's preamble should ask for 184 bytes. Function B won't have a preamble.

Generally speaking, no function will have a preamble anymore, except for virtual functions. After all, if function X calls virtual function Y, function X will have no idea what function Y will require. So, function Y will have a preamble.

(TBH, I'd be surprised if Go doesn't already do this optimization.)

### Example

Let's say we have this interface:

```vale
interface Ship {
  fn launch(&self);
  fn fire(&self);
  fn fly(&self);
}
```

And CombatShip's `launch(self &CombatShip)` called the (non-virtual) `get_target` function:

```vale
// Callable via interface
fn launch(self &CombatShip) {
  target = get_target(...);
  ...
}
// Never called via interface
fn get_target(self &CombatShip) {
  ...
}
```

Then `launch` will have a preamble, but `get_target` won't.


## Only do a preamble during concurrency

Now, let's try to make this into a zero-cost abstraction.

(The following sections are stepping stones, each slightly changing the design of the previous.)

At the beginning of every virtual function, we'll only check for stack space if we're being run concurrently, in other words, by an executor.

How do we know we're being run by an executor?

In Vale, there's something known as the "context word", which is in a thread-local (or an implicit first parameter, TBD). Its least significant bit (0x1) is the "in_concurrency" bit. If it's true, we're currently inside an executor that's running multiple virtual threads.

So, at the beginning of every virtual function, we'll e.g. `if (context_word & 0x1) { reserve_stack_space(184) }` to reserve 184 bytes of stack space.

This is pretty good so far. The next sections make it even better.


## Use wrapper functions

Let's move the if-statements and stack reserving up into wrapper functions. It may seem weird, but you'll see why in a bit.


Let's take the stack reserving out of the `fn launch(&self)` function, and put it into a wrapper function named `launch__concurrent(&self)` function.

It would look like this:

```vale
fn launch__concurrent(&self) {
  reserve_stack_space(184);
  launch(self);
  release_stack_space(184);
}
```

A `launch__route(&self)` function would call either `launch` or `launch_concurrent`:

```vale
fn launch__route(&self) {
  if (context_word & 0x1) {
    launch_concurrent(self);
  } else {
    launch(self);
  }
}
```

`launch` was previously in `CombatShip`s vtable, but now, `launch__route` will be there instead. `CombatShip`s vtable now looks like:

 * 0: launch__route(&CombatShip)
 * 1: fire__route(&CombatShip)
 * 2: fly__route(&CombatShip)

It might seem like we've only shuffled some things around, but the reasoning will be clear soon.


## Route via indirect calls

Let's change the above `launch__route(&self)` function to use two function pointers instead of an if-statement:

```vale
fn launch__route(&self) {
  // 2-element array of function pointers
  funcs = [launch, launch_concurrent];
  // Determine index of function we should call.
  index = context_word & 0x1;
  // Call the index'th function pointer
  funcs[index](self);
}
```

In a way, it's like we're making a *tiny little vtable, keyed by a boolean.*

Seems like a small nonsensical change, but the next section will finally show why.


## Use the original vtable instead

Recall that the above `launch__route` was in `CombatShip`s vtable.

Also recall that `launch__route` itself is looking up the final function pointer in a tiny 2-element vtable.

**Now, let's combine them.** We'll no longer need `launch__route`. We'll put its tiny 2-element vtable into the main `Ship` vtable.

Previously, to call `launch` on a `Ship`, we'd just call the 0th function in its vtable, which looked like this:

 * 0: launch__route(&CombatShip)
 * 1: fire__route(&CombatShip)
 * 2: fly__route(&CombatShip)

But now, `CombatShip`'s vtable will look like this:

 * 0: launch(&CombatShip)
 * 1: launch__concurrent(&CombatShip)
 * 2: fire(&CombatShip)
 * 3: fire__concurrent(&CombatShip)
 * 4: fly(&CombatShip)
 * 5: fly__concurrent(&CombatShip)

To `launch`, instead of calling the 0th function, we'll call the function at `0 + (context_word & 0x1)`.

To `fire`, instead of calling the 1th function, we'd call the function at `2 + (context_word & 0x1)`.

To `fly`, instead of calling the 2th function, we'd call the function at `4 + (context_word & 0x1)`.

As you can see, simply doing `+ (context_word & 0x1)` will determine whether we call the wrapper function. 

 * If we're not doing concurrency, `+ (context_word & 0x1)` will be `+ 0` and just call the original `launch`, `fire`, `fly`.
 * If we are doing concurrency, `+ (context_word & 0x1` will be `+ 1` and will call `launch__concurrent`, `fire__concurrent`, `fly__concurrent` which will manage the stack space.



## Analysis

This is pretty close to a zero-cost abstraction. The only performance costs are:

 * An extra load, bitwise-and, and add operation, every time we do a virtual call.
 * Also, a preamble at the beginning of every indirectly recursive function (unless there's a virtual call between).

This is much better than the original Go approach of unconditionally doing a check at the beginning of every function regardless of whether any concurrency is going on.

There is also a small binary size cost: all vtables are about twice as big, and the wrapper functions will take some space too. Luckily, they're just wrapper functions, not complete doubles like Zig's approach.


# Implementation Challenges

The only challenge is in determining how much stack space functions need, such as the above 184 bytes in the example. This needs to be determined *after* any optimizations happen, and is dependent on the target machine (x86, ARM, etc.) which means we can't use the more vanilla LLVM APIs. Some possible leads: MachineFrameInfo, TargetFrameLowering, and [hints from rustc](https://internals.rust-lang.org/t/how-to-let-rustc-compile-functions-with-segmented-stack/16380). In theory, it's possible, but worst case it might require forking LLVM.

Luckily, stack switching won't be a problem, since we implemented it as part of the Fearless FFI prototype. We can also in theory reuse Go's context switching, or Google's cooperative OS thread switching mechanisms.


# More Resources

[User-space cooperative multitasking](https://brennan.io/2020/05/24/userspace-cooperative-multitasking/) (used for the fearless FFI prototype, in fact)

[Achieving 5M persistent connections with Project Loom virtual threads](https://github.com/ebarlas/project-loom-c5m), some interesting comments about not liking async/await

Dynamic linking will slow down thread-local storage ([source](http://david-grs.github.io/tls_performance_overhead_cost_linux/))

[Notes](notes/VirtualThreadsNotes.md)
