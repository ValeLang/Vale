
maybe we can have a register or thread local pointer to the base of the stack. there we can have various bits of data, like the thread id

is pure and is in parallel are two nice bits to pass in to each func.
we could even pass in (that ptr | 1) when we call a pure func.

parallel will be 1 for easy masking and adding without shifting
pure will be 2

when we ffi, we may need to set a thread local. doesnt bode well for dynamic linking, darn.

should test this vs thread locals, see perf impact.
