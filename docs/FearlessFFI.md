
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
