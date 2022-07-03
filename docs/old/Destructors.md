what makes destructors:

-   generateArraySequenceDestructor called by makeArraySequenceType and
    > put into the temputs

-   generateRuntimeSizedArrayDestructor called by
    > makeRuntimeSizedArrayType and put into the temputs

-   generateStructDestructor is the default implementation of
    > destructor:T, called when the destructor is called.

what retrieves destructors:

-   getCitizenDestructor is only called by drop. drop produces an
    > expression.

drop is called everywhere.

So, the answer is that drop is the way to destroy things.

When we try to drop a share, it only makes a Discard2. But it also puts
a destructor into the temputs. Vivem and eventually Midas will rely on
the fact that it\'s there so they can put it in the code.
