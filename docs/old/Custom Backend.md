If we had a custom x86 or ARM backend, we could do some crazy stuff.

We could assume that pointers are 48 bits.

We could assume that itable pointers are 32 bits (or even 24?), because
theyre in the top global section of memory (at least on linux).

We could jam all sorts of stuff in those pointers.

Each region could be 32 bit pointers, if we know it wont exceed 4gb.
