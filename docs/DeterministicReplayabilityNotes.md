
These are possible improvements and considered alternatives to parts of [DeterministicReplayability](DeterministicReplayability.md).


# Possible Improvement: File-specific AssumeDeterministic

It would be nice to specify which files we can assume will be the same, and which we can assume will be different.



# Possible Improvement: More Flexible Channels

It would be nice if we could be a little bit resilient in the case of one green thread sending a bunch of messages to another green thread. Not sure if that's possible though.


# Possible Improvement: Using Pointers as Hash Keys

It would be pretty nice to be able to hash heap-allocated objects, keyed by their address.

However, it's unclear how to do this in a deterministic way.

We *could* just make it deterministic in recording mode, but even then it's not clear that it will be resilient enough; if we even just touch this map the wrong way, change its order of anything, we'll moot our recording.



# Alternative Considered: Deterministic Malloc

For a while, considered having a "relatively deterministic" allocator (even made one!) where it would always return the same addresses.

Fun fact: that's not possible, because of ASLR. We also [probably can't force it](https://stackoverflow.com/questions/6446101/how-do-i-choose-a-fixed-address-for-mmap).

But, by making the allocator use 64kb slabs, we could still control the last 16 bits of the address. We could then have the recording file write down the addresses of each slab it allocates. When releasing memory to the OS, it `MADV_FREE`s it instead of `munmap`ping it, and keeps the virtual address range for future reuse.

Also considered forking mimalloc to make it do this.

However, this approach wouldn't be as resilient to code changes. A preferable approach would allow us to add print statements, pure function calls, and even refactor the internals of the program, as long as we don't change any interaction with the FFI boundary.

For that reason, we went with the Object Index Map approach.


# Alternative Considered: Epochs

An alternative to the message ID / mutex version number scheme. We would hold all writes until all reads are done and the program stalls. Then, we would allow every thread one mutable mutex unlock, and will flush all pending messages in any channels.

Not sure if it would cause deadlocks though.


# Alternative Considered: Swap out the library

We could specify a different set of .c files when doing deterministic replayability, ones that specifically make themselves act deterministically.


# Alternative Considered: Extern Mapping

Let's say we have these functions:

```
exported func OpenFile(filepath str) int;
exported func ReadFile(fd int) str;
exported func CloseFile(fd int);
```

It might return a different file descriptor on a different run, so it's deterministic.

However, we can wrap that integer in a specialized type, and add some annotations:

```
#Mapped
exported struct FileDescriptor {
  fd int;
}

#Deterministic
exported func OpenFile(filepath str) ^FileDescriptor;

#Deterministic
exported func ReadFile(fd &FileDescriptor) str;

#Deterministic
exported func CloseFile(fd ^FileDescriptor);
```

Now, when we're recording:

 * When we call `OpenFile`, after writing it to the file, we'll add this object to the Object Index Map and the Object Index Vector, as if we're sending it to C.
 * When we call `ReadFile`, it will:
    * Write object's index to the file, as it normally would.
    * Translate it to the original value.
 * When we call `CloseFile`, after writing it to the file, we'll remove this object from the Object Index Map and the Object Index Vector, as normally happens when we destroy an object.

But, it's hard to say how we'll get the original `fd` back. TODO: Figure that out.
