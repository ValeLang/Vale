
A "Foreign Function Interface" is how a language can call into code written in another language. Vale can call into C code, as shown in [Externs](https://vale.dev/guide/externs).

[Safety in Vale](https://vale.dev/fearless) describes how we can prevent any accidental problems in C from corrupting our Vale objects. This is **Fearless FFI**, and it's heckin' difficult to implement!

This page describes the design, not necessarily how it's implemented.

# Vale FFI Relevant Background

## Immutable Objects

There are two ways Vale can receive immutable objects from C:

 * Vale calls a C function, which returns an immutable object.
 * C calls a Vale function, passing an immutable object argument.

In both cases, we'll write those objects to e.g. `recording.vale.bin` (the actual filename depends on various other factors).

### Immutable Objects are Hierarchical

We know the structure of the data C is sending, because these structs are `exported`. We use that knowledge to recursively copy the data into Vale's memory.

# How Fearless FFI Works

See https://verdagon.dev/blog/fearless-ffi

(TODO: copy it into here once it's published)



# Universal Reference Struct Layout (URSL)

When we send a mutable object reference into FFI, we need to make it a universal reference.

These are the members:

 * objectPointer (64b)
 * regionPointer (64b)
 * objectGeneration (32b)
 * regionGeneration (32b)
 * typePointer: an itable pointer (if interface) or type info pointer (if struct) (64b)
 * offsetToGeneration (16b)
 * scopeTetherMask (16b)

To make it fit in 32 bytes (256b), we'll need to scatter the offset and mask into the unused bits. There are some unused bits in:

 * objectPointer needs only 56, can lose top 8 bits
 * regionPointer needs only 52, can lose top 8 bits and low 4 bits
 * typePointer needs only 52, can top 8 bits and low 4 bits

We'll shrink those to 56, 52, and 52 bits each. Should barely fit into 32B. We'll let LLVM handle the packing.

This also means the region pointer must end in 4 zeros, meaning regions need to be at a 16-byte alignment (see RMB16BA) and itables as well (see ITN16BA).

With this, given an existing region, we can conjure a reference to an object in there.

We *might* be able to get rid of the region generation. We might scramble generations of any objects leaving their regions, so it could be said that if the generation check passes, the region check will too. We might not be doing that however (it would be nice to sometimes not need to scramble generations) so make sure first.

We need the type info even for structs, so that when Vale receives it again, it can be reasonably sure that it's the right type.


## ITables Need to be 16-Byte Aligned (ITN16BA)

Because of URSL, itables need to be 16-byte aligned so they can be compressed.
