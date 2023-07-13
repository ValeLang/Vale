

Three weak options:
A. wrc table
B. lgt table
C. control block


some complications regarding merging regions. when we merge two regions, we'd need to merge their two tables, or change all incoming references.

two cases:
M: merging two mutable regions together
I: merging a mutable sub-region into its parent immutable region

and lets also consider:
F: destroying an object in an arena region.
R: destroying a reference in an arena region.
Z: destroying an entire arena when we've already copied data out




A for M: we'd need to change all intra-region weak refs.
A for I: we'd need to change all intra-region weak refs.
A for F: when we destroy an object, need to remove it from the WRCT. or not, it's an arena so w/e
A for R: when we destroy a reference, need to decrement it from the WRCT.
A for Z: just blast it all away

B for M: we'd need to change all intra-region weak refs.
B for I: we'd need to change all intra-region weak refs.
B for F: when we destroy an object, need to remove it from the LGT.
B for R: when we destroy a reference, need to do nothing.
B for Z: just blast it all away

C for M: just merge them together.
C for I: just merge them together.
C for F: when we destroy an object, need to set something in its control block
C for R: when we destroy a reference, need to decrement its control block.
C for Z: just blast it all away. the control blocks need to be in the arena too btw.


all objects need a free() anyway. the nice thing about arenas is that we can delay it, or copy stuff out and skip all of them.

C seems the best since we can no-op merge two heap regions together.



if we have an arena subregion pointing into an imm region, dont need to update control block counts.
if we have an arena subregion pointing into a mut region, then need to update control block counts into that mut region. delaying them might suck, we wouldnt be able to blast away the entire arena, would need to actually run all the free functions.
its no longer a zero cost abstraction. hmmm.

we might want to include them in the drop() function. kind of sucks though.


