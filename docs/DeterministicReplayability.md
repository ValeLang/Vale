
Deterministic Replayability is how we can execute a program twice, and guarantee that the second run will behave exactly as the first.

This page describes the final design, though only certain parts are implemented.

# Vale FFI Relevant Background

## Immutable Objects

There are two ways Vale can receive immutable objects from C:

 * Vale calls a C function, which returns an immutable object.
 * C calls a Vale function, passing an immutable object argument.

### Immutable Objects are Hierarchical

We know the structure of the data C is sending, because these structs are `exported`. We use that knowledge to recursively copy the data into Vale's memory.

## Mutable Objects

Vale can give C references to Vale objects. These references are 32 bytes, and contain the address of the Vale object.

# Goals

We want to be able to perfectly reproduce any bug we ever encounter, no matter what.

Bonus goals:

 * We want "pure resilience", the ability to add pure function calls and printouts to our program, and be able to use the same recording.
 * We'd also like some "impure resilience", the ability to be able to refactor our program as much as possible, and still be able to use the same recording.
    * This should be possible if we don't mess with anything that happens across threading or FFI boundaries.
    * It might even be possible for some FFI, if we do our FFI right.
    * I have a feeling it's possible for some threading, but no details yet.
 * It would be nice to signal when the coder has pushed resilience too far, and their recording isn't replaying things close enough to the original run.
 * We'd like to be able to use the addresses of objects in our programs, if possible.
    * ...this doesn't seem to be possible while maintaining resilience.


# Immutable Objects

Whenever Vale receives an object from C, it will write those objects to its recording file.

Recall that immutable objects are hierarchical, and have pointers to each other. When we write to the file, we'll be writing the original pointer, *not* an offset relative to the file start. This will make the file a bit harder to read, but should save some time when recording.

When we write an object, we'll first write its address. That will help the eventual reader reconstruct the hierarchy.


# Mutable Objects

When Vale receives a handle from C, we'll write it to the recording file.

However, there's a problem here: **malloc does not return deterministic addresses.** There's no way it could; the OS returns a random virtual memory address for various reasons, and also because extern C code will nondeterministically map pages.

For this reason, we need to map the addresses ourselves. To do that, we'll need to pay attention to objects we're sending into C.

When Vale sends to C the object 0x1337ABCD.1, we'll need to write that down in the recording file. On the next run, when it sends the object 0x1448DCBA.5, we'll compare that to the recording file which says we're sending 0x1337ABCD.1, and enter that into a hash map at run-time, called the Object Index Map.

## When Recording

The Object Index Map is a `HashMap<GenRef, index>`.

The index refs to a large `vector<int>` called the Object Index Vector. It is basically a giant free-list. We'll have the "head" index right next to the table, of course.

In the recording session, when we send a gen ref into the wild, we:

 1. Find a free index in the Object Index Vector. If there isn't one, expand it. Put the gen ref there.
 1. Add the ref and its index to the Object Index Map.
 1. Set the object's "Exported" bit to 1.
 1. Write that index to the recording file.

When we delete an object, check if its Exported bit is set. If so, remove it from these tables.


## When Replaying

We only need the Object Index Vector. When replaying, it's a `vector<GenRef>`.

When we send a gen ref into the wild:

 1. Read the index from the recording file.
 1. Put the gen ref to that index in the Object Index Vector.
 1. Don't set the object's "Exported" bit to 1, it's never really used in replay mode.


# Deterministic Parallelism

Making a program deterministic in the presence of parallelism is deceptively simple.

Every OS thread will have an ID and its own recording file. The filename has the thread ID in it, such as `recording.1448.vale.bin`.

Only OS threads will get recordings. Green threads are completely deterministic because their scheduling is controlled by Vale code itself, so they don't need recordings.

There are two ways to mutably share data: Mutexes and Channels.

## Channels

Every message sent through a channel will have a hidden `message_id` integer.

In recording mode, when a thread receives a message, it will write the ID of that message to the file.

In replaying mode, when a thread asks a channel for a message, it will read from the file, and specifically wait for the message with that ID. Every waiting thread will wake up, see if that's the message they've been waiting for, and if not, go back to sleep.

Possible improvement: This might be expensive, so we may instead want some sort of way for a thread to register interest in a certain ID.


## Mutex

Recall that the only way to access mutex-guarded data is to `open` it.

Every mutex will have a hidden `version` integer and `num_reads` integer.

In recording mode, when a thread opens the mutex, we'll:

 * If opening for writing:
    * To the recording file, write 1, the mutex's current version number, and its `num_reads` integer.
    * Increment the mutex's `version`.
    * Set the mutex's `num_reads` to zero.
 * If opening for reading:
    * To the recording file, write 0 and the mutex's current `version`.
    * Increment the mutex's `num_reads`.

In replay mode, when a thread wants to unlock a mutex, it will:

 * If opening for writing:
    * Read the expected version number and num_reads from the file.
    * Wait until the mutex reaches those numbers, then open it.
    * Increment the mutex's `version`.
    * Set the mutex's `num_reads` to zero.
 * If opening for reading:
    * Read the expected version number from the file.
    * Wait until the mutex reaches that version number, then open it.
    * Increment the mutex's `num_reads`.



## Race and All

Vale will have functions equivalent to [Promise.race](https://dotnettutorials.net/lesson/javascript-promise-race-vs-promise-all/) and [Promise.all](https://dotnettutorials.net/lesson/javascript-promise-race-vs-promise-all/), names TBD.

`race` is normally nondeterministic. When it returns the first available result, we'll record its index into this thread's recording file.



# Possible Improvement: Impure FFI Resilience

One would think that we can't actually call any FFI functions in a replaying session.

We actually can, with the `#Deterministic` annotation on the function, such as:

```vale
#Deterministic
exported func print(s str);
```

Certain conditions must be met, however. Not all FFI will be replayable, but with some effort, one can make a library replayable.


## Output-only Externs

If a function only takes immutable arguments, and returns nothing, it can be marked `#Deterministic`.


## Client-Supplied IDs

We can minimize the number of nondeterministic data coming from FFI.

For example, instead of FFI returning a file descriptor:

```
exported func OpenFile(name str) int;
```

we can have the library specify the ID itself:

```
exported func OpenFile(name str, id u128);
```

and then C could do the mapping of ID to file descriptor.


Some drawbacks:

 * It would require a thread-safe global hash map though, which is unfortunate.
 * We would have to handle collisions, which might require a `FileManager` of sorts.



## Extern Mapping

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


# Possible Improvement: Using Pointers as Hash Keys

It would be pretty nice to be able to hash heap-allocated objects, keyed by their address.

However, it's unclear how to do this in a deterministic way.

We *could* just make it deterministic in recording mode, but even then it's not clear that it will be resilient enough; if we even just touch this map the wrong way, change its order of anything, we'll moot our recording.


# Possible Improvement: More Flexible Channels

It would be nice if we could be a little bit resilient in the case of one green thread sending a bunch of messages to another green thread. Not sure if that's possible though.



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
