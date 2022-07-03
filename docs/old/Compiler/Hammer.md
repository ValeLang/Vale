Hammer will flatten the function into flats, suffix F. The registers'
creation and usage must be in reverse polish order. Every register must
be used exactly once.

These are meant to be the simplest thing for a chrono-aware VM to
execute.

Though, there could theoretically be segfaults and UB.

Turns variable names into indices.

It also flattens all addressibles into boxes. "m is an addressible local
variable to a Marine" becomes "m is a reference to a box which contains
a reference to a Marine".

The reason we need to think about boxes is because JVM can't do double
pointers (nor can SQL or JS). We need to simplify this to concepts those
can understand.

In LLVM, every variable is addressible; it's almost like every variable
has a box. In jvm though, it's inefficient to give everything a box, so
we can only box things that actually need it; only things that are
mutated from inside closures.

Fun fact: the reference to the box is always final. In fact, it's always
inline too.

In other words, we need to get rid of all LocalLookup, StructLookup,
ElementLookup, because those all return addressibles.

still thinks completely in terms of addresses and references. its just a
little smarter about it.

next, we gotta change all addresses to boxes\...

Mundane variables:

-   SoftLoad2(LocalLookup2(i)) -\> MundaneLocalLoad3(i), which just
    > gives the reference (and maybe nulls it, if its a move).

-   Mutate2(LocalLookup2(i), expr) -\> MundaneLocalMutate3(i, expr)

When putting a mundane into a closure struct, the above SoftLoad2 form
is used.

Mundane members:

-   SoftLoad2(StructLookup2(s, name)) -\> MundaneMemberLoad3(s, name),
    > which just gives the reference (and maybe nulls it, if its a
    > move).

-   Mutate2(StructLookup2(s, name), expr) -\> MundaneMemberStore3(s,
    > name, expr)

Boxed variables:

-   SoftLoad2(LocalLookup2(name)) -\> AddressibleLocalLoad/Move3(name),
    > which just gets the contents (and maybe nulls the contents, if its
    > a move).

-   Construct2(\..., LocalLookup2(name), ...) -\>
    > AddressibleLocalAddress3(name), which gets the box reference, and
    > hands it to the construct expression.

-   Mutate2(LocalLookup2(name)) -\> AddressibleLocalStore3(name, expr),
    > which re-points the contents.

Structs containing addressible members (in other words, closure
structs):

-   Mutate2(StructLookup2(expr, name), expr) -\>
    > AddressibleMemberStore3(expr, name, expr), which re-points the
    > contents.

-   SoftLoad2(StructLookup2(expr, name)) -\>
    > AddressibleMemberLoad/Move3(name), which just gets the contents
    > (and maybe nulls the contents, if its a move).

-   Construct2(\..., StructLookup2(name), ...) -\>
    > AddressibleMemberAddress3(name), which gets the box reference, and
    > hands it to the construct expression.

Arrays can't contain addressible members, we need to get rid of them:

-   Mutate2(ElementLookup2(arrExpr, indexExpr)) -\> ArrayStore3(arrExpr,
    > indexExpr), which re-points the element in the array.

-   SoftLoad2(ElementLookup2(arrExpr, indexExpr)) -\>
    > ArrayLoad3(arrExpr, indexExpr), which gets the element (and maybe
    > nulls it if its a move).

Now we have something perfect for a chrono-aware simple VM.
