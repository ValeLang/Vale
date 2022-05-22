
# Pointers in Serialize Buffers Can Be Offsets (PSBCBO)

When we write into a buffer that's destined for C consumption, that buffer will contain structs which contain pointers to other places in that same buffer. Because of this, C can read it nicely.


However, sometimes we want to serialize for a recording file, not for C. In this case, these addresses should be relative to the file begin (in other words, the buffer begin). (Note that even though it's destined for a file, we're still writing it into a temporary buffer, which we'll later fwrite to a file.)


So, instead of writing the pointer to the buffer, we subtract a certain "Serialized Address Adjuster" value from it. This value is **not**necessarily the address of the start of the temporary buffer. The recording file might have recorded a hundred calls before now, which each had their own temporary buffer, and we might be 10k into the file now. In that case, the address adjuster will be (temporary buffer begin addr) - 10k. When we subtract that from a pointer, we'll write the correct integer into the file.


When we're sending something into C, the Serialized Address Adjuster will be 0.


The address adjuster is stored in the region object. getSerializedAddressAdjuster will read it.


When we create the Linear region object, we supply a boolean called the Address Mode. If the Address Mode is 0, we're just using regular pointers. If it's 1, then we're using offsets, and we also specify where in the containing file this buffer begins. If it's 500, then the offset 500 points at the start of our buffer.



## Pointers in Registers Can Be Offsets (PRCBO)

Recall PSBCBO, and how pointers in the serialized buffer can actually be offsets. One might consider doing the translation when we read it from the serialized buffer, so that when we hold it in a register, it's a regular pointer, not relative to anything. However, that's not how it works.


We actually dont translate it, it can be an offset even when in a register.


Later, when we dereference (to store or load, from a struct or array) that's when we do the translation.


See PRCBOR for why we went this way.




