
ask schnatsel: if golang is 2k, and doesnt shrink/release memory, is google's approach basically the same?

could we plug a user allocator into the thing that allocates stacks for a future? or parallel or something.

speaking of which, perhaps future.advance should take an allocator. maybe through the context system? or maybe a thread local?

if we're running a single future, can just be a stack. if running multiple, should be a normal heap with a free list probably. default can just be malloc.


we can do something where if we send a message to a green thread thats waiting on one, we can just execute it directly, no need to go through the scheduler. we'll call it "fast channels" or "channel calling" or "channel skipping"


can use a https://en.wikipedia.org/wiki/Treiber_stack for a free-list of stack chunks, or FFI stacks

`sched_setaffinity` might help us influence threads onto a certain cpu

determine the current CPU, if we want to get the current core's number: https://en.wikipedia.org/wiki/CPUID (1FH) or `sched_getcpu` / `getcpu` 

tentative plan, pending benchmarking:
- when we launch a bunch of sub-threads in an e.g. `parallel foreach`, we'll use `sched_setaffinity` to influence them all onto the same core as the current thread
- each thread, when trying to allocate a new chunk of stack for a recursive or virtual call, will use a trieber stack for its stack chunks
- each thread, when trying to do FFI, will use a trieber stack of 8mb chunks for that, perhaps even a trieber stack for the current core known via a `getcpu`-like instruction if that exists

Precedent advice: https://stackoverflow.com/questions/37523936/how-to-set-affinity-on-multiple-cpus-by-sched-setaffinity#comment62552536_37523936

Caution: https://news.ycombinator.com/item?id=10929385

JVM has a `Thread.park` which could have some useful mechanisms.