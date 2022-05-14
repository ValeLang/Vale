
maybe we can have a register or thread local pointer to the base of the stack. there we can have various bits of data, like the thread id

is pure and is in parallel are two nice bits to pass in to each func.
we could even pass in (that ptr | 1) when we call a pure func.

parallel will be 1 for easy masking and adding without shifting
pure will be 2

when we ffi, we may need to set a thread local. doesnt bode well for dynamic linking, darn.

should test this vs thread locals, see perf impact.


# Alternatives For Purity Across the FFI Boundary (AFPAFFIB)

(See PAFFIB)

## Option A: Region Metadata Has Pure Bit (RMHPB)

Region objects currently have this:

 * Generation
 * Allocate function pointer
 * Deallocate function pointer

We can add:

 * isImmutable bit

Whenever we want to call a pure function, making a region temporarily immutable, we'll:

 * Save the old bit
 * Write 1 to the region's bit.
 * Call the pure function
 * Restore the old bit.

Luckily, main's region object, and any temporary function region object, will all be on the stack and very cache-hot, so the slowdown shouldn't be too intense.

This is improved on by WRMBVC.

## Option B: Write Region Metadata Bit on Virtual Call (WRMBVC)

We can do some static analysis to see which pure functions might call externs. If a pure function calls an extern, it will note that it does so, and it will do RMHPB's bit writing. It will also broadcast to all of its callers that it may do some FFI, so they should store their immutable bits before calling it too.


The one loophole is in virtual calls, where we have no idea where they'll end up. So, we'll treat those the same as FFI: pure functions before doing virtual calls will write their bits, and instruct their callers to do the same.


We can also optimize, and do the write once earlier, if we know we'll call pure functions on a region 100 times. Then we can restore it once at the end.


## Option C: Two Kinds of Pure

This builds on RMHPB.


The context word can actually have two bits: one about whether we're in a pure function, and one about whether we're allowed to call FFI.


When we're doing a pure call that isn't allowed to call FFI, we'd set the latter to 1, then we can be confident that nobody will call an extern.

When we're doing a pure call that is allowed to call FFI, we'd write the bit to the metadata, like RMHPB.

When we call an extern, we assert that that bit is 0.


## Option D: Force C to Carry Context Words


Somehow have C carry the region pointer so that it can be given back to the function in vale. in other words, just how vale carries it along in every function, make C do that too. it has to convey all the context words.

Or rather, we'll give it encrypted pointers to where the context words are on the stack.


## Option E: Travel up the stack to find the most recent context word

This is only really applicable to function-bound regions, and mutexes that are open too, I guess. We'll crawl up the vale stack, looking for context words for this particular region, then we'll use those.


## Option F: Only Allow Opening a Subset of the Last Vale Function's Regions

(See PAFFIB, this is what we went with)

## Comparison

F and maybe E seem like the leading contenders.

A isn't as good as B.

If we need to flush bits, then B is the best. It's unclear if we need to, though.

Let's not go with C. Having two kinds of pure seems like it could backfire, and we'd have to make that choice for every pure function. We'd end up manually bifurcating, two versions of every pure function.

D seems workable, but it seems like more effort and strictly not as good as F.

E is a little more expensive than F, and also a lot harder to implement... but it is more flexible. We don't have to artificially carry regions all the way to wherever there's an extern.

F has a complication, but it honestly seems like an edge case.

Let's go with F for now.
