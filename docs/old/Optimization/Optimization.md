**AOSOA**

See
[[https://www.reddit.com/r/ProgrammingLanguages/comments/8ars0b/language_supported_aosoa/]{.underline}](https://www.reddit.com/r/ProgrammingLanguages/comments/8ars0b/language_supported_aosoa/)
for how we can do AOSOA with especially the contiguous uniform pool.

fat pointers with ref count diffs? and theyre committed to the actual
ref count when we point at a different object or the ref goes out of
scope or something. that might be faster because it\'s operating on
registers and stack instead of going into memory to read the object?

if im the only one with a reference to this immutable data structure,
and i want to modify it, then i\... can.

if we have reference counting, we can check this, unless we do
optimizations. i think.

if something is shallowly uniquely referenced, then we can definitely
modify it. thats annotations thats easy to produce, too.
