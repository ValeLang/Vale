# PRCBO Reasoning (PRCBOR)

When we read a struct from a linear buffer, and the linear buffer has offsets rather than pointers inside (such as if we're writing to a file), then what happens when we load one of those offsets? Do we immediately translate it to a pointer, by adding the Serialized Address Adjuster?


## Option A: Immediately Add Serialized Address Adjuster

This would seem nice because then, when something's in a register, it's definitely a valid pointer to some memory.

We didn't go with this.

The problem here is that doing this adjustment is pretty tricky. If we're loading an interface pointer, we need to open up the fat pointer, adjust the object pointer within, and reassemble the fat pointer. That's not too bad, but it gets worse if it's an inline interface, an enum. We'd have to then dive into whatever structs are contained, and translate them as well. We'll end up making an entire hierarchy of functions just to do this translation.

So instead, we'll go with the other approach.


## Option B: Don't Immediately Add Serialized Address Adjuster

This is what we went with.


Here, when we read a reference from the serialized buffer, it may be an offset instead of a pointer. When we eventually load from a struct or an array, we'll translate that possibly-offset into a pointer and immediately load from it.


One unfortunate part is that it might have a lot more additions. However, I think those can be optimized out, they're very repeated. We'll just need to have good use of restrict pointers perhaps.
