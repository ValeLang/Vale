(This is internal documentation and notes, feel free to drop by the discord server if you have any comments or suggestions.)

Deterministic Replayability (also known as Perfect Replayability) is how we can execute a program twice, and guarantee that the second run will behave exactly as the first, **even in the presence of multithreading.**

This page describes the final design, though only certain parts are implemented.

# What, Why

One of the biggest headaches in any debugging session is *reproducing the problem.* There are so many uncontrollable factors that affect whether a bug happens:

 * Network latency
 * Thread scheduling
 * Time of day
 * Random number generators
 * User input
 * Animation delays

It can be nigh impossible to reproduce certain bugs.

However, if we design a language from the ground up to be _deterministic_ and record the above unpredictable inputs, we can completely solve the reproducing problem, once and for all.

Here's how we'll make it happen.

# Vale FFI Relevant Background

## Immutable Objects

There are two ways Vale can receive immutable objects from C:

 * Vale calls a C function, which returns an immutable object.
 * C calls a Vale function, passing an immutable object argument.

### Immutable Objects are Hierarchical

The Vale compiler knows the structure of the data C is sending, because these structs are `exported`. We use that knowledge to recursively copy the data into Vale's memory.

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



# FFI Resilience

One would think that we can't actually call any FFI functions in a replaying session.

We actually can, with the `#AssumeDeterministic` annotation on the function, such as:

```vale
#AssumeDeterministic
exported func print(s str);
```

Certain conditions must be met, however. Not all FFI will be replayable, but with some effort, one can make a library replayable.


## Pure FFI Resilience

If a function only takes immutable arguments, and returns nothing, it can be marked `#AssumeDeterministic`, and it will happen during replays. `print` is a good candidate for this.


## Impure FFI Resilience

Basically, we can minimize the number of nondeterministic data coming from FFI.

For example, instead of FFI returning a file descriptor:

```
exported func OpenFile(path str) int;
```

we can have the library specify the ID itself:

```
exported func OpenFile(path str, id u256);
```

and then C could do the mapping of ID to file descriptor.

Even better than a u256, we could send a gen ref:

```
func OpenFile(path str) ^File {
  file = ^File();
  OpenFileExtern(path, &file);
  return file;
}
exported func OpenFile(path str, id &File);

func CloseFile(file ^File) { ... }
```

as gen refs are guaranteed to be unique throughout their lifetime.

The only downside here is that it requires a thread-safe global hash map on the C side to do the mapping, which is unfortunate but doable.

Now we can mark all these functions as `#AssumeDeterministic` and let their calls happen even in replaying mode.


# Notes

See [DeterministicReplayabilityNotes](DeterministicReplayabilityNotes.md).
