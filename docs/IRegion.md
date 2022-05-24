
# IRegion Interface in Backend (IRIIB)

IRegion is the main class (well, interface) responsible for managing and accessing memory in the backend.

A random sample of some of the methods on IRegion:

 * allocate: allocates a struct and populates its members.
 * constructStaticSizedArray: allocates a static sized array.
 * loadMember: loads a member from a struct.
 * receiveUnencryptedAlienReference: copies an object from another region.
 * getRuntimeSizedArrayLength: gets an array's length.

There are a handful of different subclasses:

 * GenerationalRegion (nee ResilientV3): A region using generational memory. Used for:
    * Immutable regions
    * The main mutable region, if --region-override=generational.
 * HybridGenerationalRegion (nee ResilientV4): A region using HGM.
    * The main mutable region by default or if --region-override=hybrid-generational.
 * UnsafeRegion: A region using no memory safety. Used for:
    * Unsafe blocks.
    * The main mutable region, if --region-override=unsafe.
 * RCImmRegion: A region for immutable objects.
    * Might not be in the final design, depending on how/whether we share iso regions around.
 * NaiveRCRegion: A region using naive reference counting. Won't use inline interfaces. Its main purpose is for benchmarking against.
    * The main mutable region, if --region-override=naive-rc
 * LinearRegion: A bump allocator, only used for:
    * Sending objects to C
    * Writing objects to a temporary buffer for a file.
    * (maybe someday) Writing objects for network messages.

With the IRegion interface, the backend stage can compile expressions against a common lower interface.

GenerationalRegion and HybridGenerationalRegion dont necessarily use malloc for their allocating and deallocating, they may use an allocator behind a function pointer instead.


# Notes

Need to revisit whether kinds should be associated with regions. probably so that unsafe ones' non-owning pointers can be compatible with C, and GM's ones can be fat pointers. but itd be nice if there was a different way.
