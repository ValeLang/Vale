HGM has a pretty long history.

It started with regular generational memory, which was 

# V0

This was back when Vale was built upon constraint references, back when [The Next Steps for Single Ownership and RAII](https://verdagon.dev/blog/raii-next-steps) was published.

We introduced a "resilient mode", for cases where we need complete memory safety and need to not halt whenever a pointer starts dangling.

The first version of resilient mode was called "resilient-v0".

Basically, it made all non-owning references from constraint references into weak references.

Weak references used something called a "weak reference count table" (WRCT) to store its weak ref counts.

# V1

Instead of having a table storing the reference counts, we have a table storing the generations, called the Local Generation Table (LGT).



# V3

generational memory with sacred integers

keep in mind we have side stacks.


# V4 generational memory with scope tethers

the true beginning of HGM.

# V5 add static analysis

# V6 generation in object itself








When we move an object to another heap, we increment the generations of it and all of its indirectly owned objects.




# V7-V10

These are lost to time. They were likely some unnecessary circuitous intermediate steps that we later walked back.


# V10 Compaction

These sacred integers mean we can never release memory back to the OS. However, we can do some clever merging tricks (in retrospect, similar to MESH). See https://lobste.rs/s/sglvcc/generational_references_2_3x_faster_than#c_gqakeg for a rough overview of the plans there.
