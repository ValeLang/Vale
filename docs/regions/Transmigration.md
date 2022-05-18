

# Isolate When Returning from Pure Function

When we return an object from a pure function, making it cross over from the temporary region into the caller's region, we'll need to isolate it and everything it indirectly owns, so it can't dereference any objects from its old region. After all, we're blasting that old region away.


Alternatively, we could copy the objects out, if that makes blasting away the pure function a little faster (perhaps using mi_heap_destroy).


# How to Isolate an Object Hierarchy

First, make a random 32-bit number X. Then, recurse through the entire object, incrementing all non-owning references by X and incrementing all object generations by X.

If any non-owning reference points within the object hierarchy, the non-owning reference and the object's generation will both be adjusted by the same X, so it'll still match. If a non-owning reference points outside the object hierarchy, the non-owning reference will no longer match its target object, which is what we want.
