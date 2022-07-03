in null mode, we can have a boolean alongside every stack variable
saying whether it\'s null. if it is, then any function that uses it will
evaluate to null. eventually it will be consumed and thrown away by a
mut, supposedly\... or we return it from main, in which case it\'s an
error.

a null can never be stored inside a struct because of this.

when we call a function, we && every argument together.

we can do some clever things with bit vectors to know whether
something\'s null. 0 means not null, 1 means null. lets say a function
takes locals 4, 5, and 8. that\'s bits 0x8, 0x10, and 0x80. or them
together to 0x98. & that with the null bit vector. if anything, then the
function result is null.
