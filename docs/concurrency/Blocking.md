
# Async & Blocking in Vale

Vale is bringing an entirely new approach to concurrency, by using mechanisms from Rust's async/await, Go's goroutines, algebraic effects, and structured concurrency. These should combine to form something efficient and easier to use than existing approaches.

For an overview of the user experience, see [Seamless, Fearless, Structred Concurrency](https://verdagon.dev/blog/seamless-fearless-structured-concurrency). TL;DR: A user can add `parallel` or `concurrent` in front of a foreach loop to run each iteration in a different task or thread.

The underlying mechanism is a blend of async/await and goroutines, described in [VirtualThreads.md](VirtualThreads.md).

However, there are a few issues remaining, around **accidental blocking.** Shnatsel explained it best (thanks Shnatsel!):

> If you have 100 tasks running on a single OS thread, and any one of them does some intensive computation for 1 second instead of quickly yielding to the scheduler, then all the tasks on the thread just got delayed by 1 second. Web servers are just about the only real user of cooperative (everything else works fine with OS threads) and adding 1 second of latency to all ongoing connections is devastating, but also very hard to avoid, because the programmer needs to manually keep track of all instances where that could happen and manually split them into smaller chunks with await points in between to prevent them. This is an issue in all languages with explicit async/await and more generally any environment where cooperative multitasking has been retrofitted.

> Languages like Erlang and Go that were designed for cooperative multitaksing from the ground up and provide "green threads" combat this by automatically injecting yield points into the code; so the code will yield to the scheduler from time to time no matter what. Getting this right was tricky (Go had issues with tight loops for a while) but it works as long as all your code is written in that language. 

> Even that approach falls apart in the presence of FFI, however. If you call into a C library you can no longer inject yield points into it. 

> The only sure-fire solution to blocking in FFI that I'm aware of is Google's cooperative threads, where the fallback in case a thread doesn't explicitly yield to another thread is just getting preempted by the OS scheduler. I know Rust's async-std tried to paper over this by spawning another thread and running the pending tasks there, leaving the blocked thread to its fate - but that design had other issues and never landed. It also relied on the tasks being thread-safe in the first place, which may not be the case in Vale. Their post about it cites Go as inspiration, so I assume Go does something similar as well, but I just don't know how well that works.

So, this doc explores the problem and some options that could help with accidental blocking.


## Cooperative Yielding Approach

In all of these approaches, we use the approach in [VirtualThreads.md](VirtualThreads.md), specifically that we expand the stack on any recursive or virtual call if more space is needed.

This is often done as part of structured concurrency, as described in [Seamless, Fearless, Structred Concurrency](https://verdagon.dev/blog/seamless-fearless-structured-concurrency), with a `parallel foreach` loop. The thread that launches tasks this way is called the "original thread".

In these approaches we use **cooperative yielding**, in other words the task will give up control to the most recent `parallel foreach` loop so it can make some progress on another iteration for a while.

Cooperative yielding can use any underlying stack-switching mechanism, such as [user-level threads](https://www.youtube.com/watch?v=KXuZi9aeGTw) if they [reach Linux](https://www.phoronix.com/scan.php?page=news_item&px=Google-User-Thread-Futex-Swap)), [cooperative user-space multitasking](https://brennan.io/2020/05/24/userspace-cooperative-multitasking/), or Go's stack-switching mechanism. The mechanism we use for that is orthogonal / out of scope for this doc, this doc is about **what to do if a task goes a very long time without yielding.**

### Consistently Yielding Ecosystem

A major source of blocking is when someone accidentally uses a blocking system call.

For example:

 * Rust has a `Mutex` class with a `lock` method which should never be used during cooperative concurrency, because it blocks the entire thread. One should instead use async_std's `Mutex` whose `lock` is `async` and therefore yields.
 * Rust's `fs::read_to_string(filename)` will block. People should instead use `tokio::fs::File`.

Vale wouldn't have this problem. Because it's colorless (see [VirtualThreads.md](VirtualThreads.md)) we can yield at any point during any function if we want to, and the entire standard library yields on all long-lived operations, such as mutex locking or reading from files or sockets.


### Time Budgets

Tokio uses ["automatic cooperative task yielding"](https://tokio.rs/blog/2020-04-preemption). Basically, each task has a time budget, and if it hits a possible yield point and its time budget is used up, it will yield even if it _could_ progress.

We'll build that functionality into Vale's yielding.


### Spawning for CPU Intensive Usage

A common source of unwanted task blocking is when a task needs to do a lot of heavy CPU computation.

Tokio solves this with [spawn_blocking](https://docs.rs/tokio/1.0.1/tokio/task/fn.spawn_blocking.html) which will fire up a thread for the calculations:

```
use tokio::task;

let res =
    task::spawn_blocking(move || {
        // do some compute-heavy work or call synchronous code
        "done computing"
    }).await?;

assert_eq!(res, "done computing");
```

In Vale, this would be doable with the `parallel` keyword in front of a regular `block` statement, like:

```
res =
    parallel block {
        // do some compute-heavy work or call synchronous code
        "done computing"
    }

assert_eq(res, "done computing");
```

### Spawning for FFI

Another common source of unwanted task blocking is when a task calls into FFI which blocks or does some heavy CPU computation.

For that, we would put `parallel` in front of the FFI function's declaration:

```
parallel extern func readFile(filename str) str;
```


### Monitor Thread

The above measures (consistently yielding ecosystem, time budgets, `parallel` blocks and FFI) solve the blocking problem for well-behaved applications.

However, we might still have some _accidental_ blocking, in our code or our dependencies' code.

So, when we do a `parallel foreach` loop, we'll also launch a "monitor thread".

The monitor thread will periodically looks into the original thread's memory to see how long it's been running the current task.

When it sees that a task has been running too long, the monitor thread will:

 * Steal the work from the original thread and start running it in a new thread.
 * Mitigate the original thread's CPU usage. See below for two possible ways to do that.


#### Pinning Misbehaving Tasks

To mitigate the original thread's CPU usage, the monitor thread can **pin the original thread** to a single "CPU computation" core that's shared with all other non-behaving threads like this.

We then let the OS scheduler figure out the scheduling for all those CPU intensive threads.


#### Suspending Misbehaving Tasks

To mitigate the original thread's CPU usage, the monitor thread can **suspend the original thread** for a while.

On Windows, we can use `SuspendThread`. (TODO: Double check this actually works like we think it does.)

On POSIX, we could send a thread signal to the original thread. Then we look up the stack (or into a thread local) to see what the thread is doing:
  * If it's in some sort of CPU-intensive user code, the signal handler can just force a yield.
  * If it's in FFI, force a yield.
  * If it's in some sort of uninterruptible Vale builtin function, then set a thread-local bit. The Vale builtin function, when it ends, will check for that bit and yield if it's set.

The monitor thread then periodically wakes up the thread (maybe every 50ms?) for a few milliseconds so it can progress.


### Monitor Thread Drawbacks

Tokio has done some [research on the topic](https://tokio.rs/blog/2020-04-preemption#a-note-on-blocking) and have concluded that monitor threads aren't a very good fit, because:

 * It's difficult to detect scenarios where spawning a new thread might actually _reduce_ throughput.
 * It's vulnerable to bursty or uneven workloads.

It's unclear whether CPU pinning and/or suspending will help these drawbacks.

Also, if we make the monitor thread opt-in, the user can decide whether it would be appropriate for their use case. The Tokio post suggests that it would be good for coarse-grained tasks, such as ones taking 100ms or more.


## OS Threads with Vale Virtual Stacks

All of the above talks about using cooperative yielding. **This is an alternative, which does not use cooperative yielding.** It instead makes OS threads use less space for their stacks.

TL;DR: Use the mechanism from [VirtualThreads.md](VirtualThreads.md) which expand the stack whenever there's a recursive or virtual call... _for regular OS threads._ We'll call them SmallThreads.

It uses the same exact mechanism as described in [VirtualThreads.md](VirtualThreads.md). Phrased in terms of SmallThreads:

 * Whenever we want to launch a SmallThread, we allocate a small stack for it with malloc (PTHREAD_STACK_MIN plus the stack space needed for the Vale function) and set it using [pthread_attr_setstacksize](https://man7.org/linux/man-pages/man3/pthread_attr_setstacksize.3.html) and [pthread_attr_setstack](https://docs.oracle.com/cd/E19120-01/open.solaris/816-5137/attrib-95722/index.html).
 * Have a thread-local (or a context word passed down through every function) which contains a bit called `isInSmallThread`, initialize it to 1 for SmallThreads, otherwise 0.
 * On any virtual call if `isInSmallThread` is true, and on any recursive function, we check if we need to malloc another chunk of memory for the stack.


This solves the age-old drawback of threads, that they usually need 8mb of stack space (and at least 4kb of physical memory).

Advantages:

 * We can use the OS's regular scheduler which already ensures fairness between all threads. It solves the blocking problem.

Possible concerns and mitigations:

 * The context switching overhead is still sometimes significant, since these are regular OS threads.
    * This _might_ be solved by [pinning threads to specific cores](https://github.com/jimblandy/context-switch): "The async advantage also goes away in our microbenchmark if the program is pinned to a single core."
 * It takes a while to create and destroy threads.
    * This is solvable with thread pooling.
 * It would be hard to share a pool of allocated chunks of memory between multiple threads. We might as well rely on malloc for that, as it's pretty good at managing memory across multiple threads.
    * This downside exists for Rust async as well, so we're at least not worse than the baseline.
 * We'd need some extra stack space when calling into FFI.
    * Perhaps we would put this work item onto some work queue, so a thread with a bigger stack can execute the FFI call?

Either way, this can be offered as an option to the user, so they can experiment and see if this or cooperative yielding works better for them.


## Notes

### Design Constraints and Advantages

There are some aspects of Vale which sometimes mean we can't do what other languages do.

 * Vale code can have pointers to objects on the stack (like C/C++/Rust, and unlike GC'd languages) which means we generally can't copy stacks around like Go or Loom can.
 * Vale aims to be extremely fast, so we might not be able to inject "safe points" into the code like Go does.
 * Vale needs to remain 100% memory-safe.

Of course, Vale has some advantages too. Knowing these might help us figure out more approaches.

 * Vale is a high level language, which means we can introduce abstractions that other languages can't. For example:
    * Generational references are generally impossible in C++ or Rust.
    * We don't have to treat tasks and threads differently, we can decide that they're the same thing, just run differently.
 * We can decide to not directly expose some use cases if they're too niche (and then delegate them to FFI), such as thread local storage.
 * The region borrow checker allows us to know when something's immutable.

