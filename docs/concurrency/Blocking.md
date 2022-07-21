
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

# Design Constraints and Advantages

There are some aspects of Vale which sometimes mean we can't do what other languages do.

 * Vale code can have pointers to objects on the stack (like C/C++/Rust, and unlike GC'd languages) which means we generally can't copy stacks around like Go or Loom can.
 * Vale aims to be extremely fast, so we might not be able to inject "safe points" into the code like Go does.
 * Vale needs to remain 100% memory-safe.

Of course, Vale has some advantages too:

 * Vale is a high level language, which means we can introduce abstractions that other languages can't. For example:
    * Generational references are generally impossible in C++ or Rust.
    * We don't have to treat tasks and threads differently, we can decide that they're the same thing, just run differently.
 * We can decide to not directly expose some use cases if they're too niche (and then delegate them to FFI), such as thread local storage.
 * The region borrow checker allows us to know when something's immutable.


# Approach 1: OS Threads with Vale Virtual Stacks

It seems like OS threads are actually pretty good in a lot of cases, especially because they use the OS scheduler which is pretty good at fairness.

The only drawbacks are:

 * They they use a lot of space for the stack: 8 megabytes on linux!
 * The context switching overhead can sometimes be significant.

However, we might be able to solve that first one. If we can create an OS thread backed by **our own allocation strategy** from Vale Virtual Threads, we can get the advantages of OS threads without the high memory usage.

We can use our own memory for a thread's stack, using [pthread_attr_setstacksize](https://man7.org/linux/man-pages/man3/pthread_attr_setstacksize.3.html), [pthread_attr_setstack](https://docs.oracle.com/cd/E19120-01/open.solaris/816-5137/attrib-95722/index.html) with malloc, with PTHREAD_STACK_MIN to factor into the size.

Possible downsides:

 * It would be hard to share a pool of allocated chunks of memory between multiple threads. We might as well rely on malloc for that, as it's pretty good at managing memory across multiple threads. Though, this downside exists for Rust async as well, so it's not too unacceptable.
 * We'd need some extra stack space when calling into FFI.
    * Perhaps we would put this work item onto some work queue, so a thread with a bigger stack can execute the FFI call?


# Approach 2: Just Do What Tokio Does™

We can have our thread (the "original thread") launch a bunch of tasks using cooperative concurrency, as described in [VirtualThreads.md](VirtualThreads.md). This can use any underlying stack-switching mechanism, such as [user-level threads](https://www.youtube.com/watch?v=KXuZi9aeGTw) if they [reach Linux](https://www.phoronix.com/scan.php?page=news_item&px=Google-User-Thread-Futex-Swap)), [cooperative user-space multitasking](https://brennan.io/2020/05/24/userspace-cooperative-multitasking/), or Go's stack-switching mechanism.

Tokio uses ["automatic cooperative task yielding"](https://tokio.rs/blog/2020-04-preemption). Basically, each task has a time budget, and if it hits a possible yield point and its time budget is used up, it will yield even if it _could_ progress.

Vale is even more naturally suited to this approach because its entire standard library can conform to this scheme, not just Tokio-aware resources.


# Approach 3: Monitor Thread

Like Approach 2, we can have our thread (the "original thread") launch a bunch of tasks using cooperative concurrency, as described in [VirtualThreads.md](VirtualThreads.md). This can use any underlying stack-switching mechanism, such as [user-level threads](https://www.youtube.com/watch?v=KXuZi9aeGTw) if they [reach Linux](https://www.phoronix.com/scan.php?page=news_item&px=Google-User-Thread-Futex-Swap)), [cooperative user-space multitasking](https://brennan.io/2020/05/24/userspace-cooperative-multitasking/), or Go's stack-switching mechanism.

First, note that the Vale stdlib will never block; it always yields before any blocking.

We'll give the user some tools to make their program more well-behaved:

 * The user can spawn child threads for their CPU-intensive sections.
 * The user can annotate `extern` functions with `#Async` to put them on a work queue and yield until they're done.

We still need to handle "misbehaving" tasks, which do CPU intensive calculations or block in FFI. For this, we'll use a monitor thread, explained below.

The monitor thread will periodically looks into the original thread's memory to see how long it's been running the current task.

When it sees that a task has been running too long, the monitor thread will:

 * Steal the work from the original thread and start running it in a new thread.
 * Mitigate the original thread's CPU usage. See below for two possible ways to do that.


## Approach 3A: Monitor Thread with Pinning

To mitigate the original thread's CPU usage, the monitor thread can **pin the original thread** to a single "CPU computation" core that's shared with all other non-behaving threads like this.

We then let the OS scheduler figure out the scheduling for all those CPU intensive threads.


## Approach 3B: Monitor Thread with Suspending

To mitigate the original thread's CPU usage, the monitor thread can **suspend the original thread** for a while.

On Windows, we can use `SuspendThread`. (TODO: Double check this actually works like we think it does.)

On POSIX, we could send a thread signal to the original thread. Then we look up the stack (or into a thread local) to see what the thread is doing:
  * If it's in some sort of CPU-intensive user code, the signal handler can just force a yield.
  * If it's in FFI, force a yield.
  * If it's in some sort of uninterruptible Vale builtin function, then set a thread-local bit. The Vale builtin function, when it ends, will check for that bit and yield if it's set.

The monitor thread then periodically wakes up the thread (maybe every 50ms?) for a few milliseconds so it can progress.


## Approach 3's Drawbacks

Tokio has done some [research on the topic](https://tokio.rs/blog/2020-04-preemption#a-note-on-blocking) and have concluded that monitor threads aren't a very good fit, because:

 * It's difficult to detect scenarios where spawning a new thread might actually _reduce_ throughput.
 * It's vulnerable to bursty or uneven workloads.

It's unclear whether CPU pinning and/or suspending will help these drawbacks.



https://users.rust-lang.org/t/blocking-tasks-in-async-std/70660/13







