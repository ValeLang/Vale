When we paralellize our pure functions, we'll be doing a lot of
repetitive operations. SIMD might be useful for that.

[[https://stackoverflow.com/questions/52389997/can-i-use-simd-to-bucket-sort-categorize?noredirect=1#comment91727733_52389997]{.underline}](https://stackoverflow.com/questions/52389997/can-i-use-simd-to-bucket-sort-categorize?noredirect=1#comment91727733_52389997)

Says:

-   This is similar to the histogram problem

-   vpconflictd might be useful, since it detects conflicts.

-   SIMD is probably not worth it since we're still bottlenecked on
    > cache.

Actually... lets say:

-   We have 8000 elements.

-   They're in 10 categories.

-   We can do 4 operations at once.

For each of the 10 categories, we'd make 4 parallel arrays, of size 2000
each.

Slot 0 of the SIMD would go into the 0th array for the category.

Slot 1 of the SIMD would go into the 1st array for the category.

Slot 2 of the SIMD would go into the 2nd array for the category.

Slot 3 of the SIMD would go into the 3rd array for the category.

In the end, we'd end up with 4\*10\*(8000/4) elements, 80000.

But it's immune to conflicts!

But it's scattered everywhere, into 40 arrays instead of 10.

Then bring them together, maybe even with threads/coroutines.
