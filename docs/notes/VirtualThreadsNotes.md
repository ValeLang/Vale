
ask schnatsel: if golang is 2k, and doesnt shrink/release memory, is google's approach basically the same?

could we plug a user allocator into the thing that allocates stacks for a future? or parallel or something.

speaking of which, perhaps future.advance should take an allocator. maybe through the context system? or maybe a thread local?

if we're running a single future, can just be a stack. if running multiple, should be a normal heap with a free list probably. default can just be malloc.


we can do something where if we send a message to a green thread thats waiting on one, we can just execute it directly, no need to go through the scheduler. we'll call it "fast channels" or "channel calling" or "channel skipping"
