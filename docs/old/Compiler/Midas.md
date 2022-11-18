# Use Locals To Make Constant Into Expression

(ULTMCIE)

It turns out,

LLVMConstInt(LLVMInt64Type(), constantI64-\>value, false)

doesn\'t actually make an expression and register in LLVM, it makes a
constant.

For example:

fn main() {

= if (true) {

1337

} else {

1448

}

}

will result in this ll:

define i64 @\"F(\\22main\\22)\"() {

block1:

br i1 true, label %block2, label %block3

block2:

block3:

block4:

%0 = phi i1 \[ 1337, %block2 \], \[ 1448, %block3 \]

ret i1 %0

}

When we really want some sort of \"put constant into register\"
instruction, like:

define i64 @\"F(\\22main\\22)\"() {

block1:

br i1 true, label %block2, label %block3

block2:

%0 = 1337

block3:

%1 = 1448

block4:

%2 = phi i1 \[ %0, %block2 \], \[ %1, %block3 \]

ret i1 %0

}

However, there is no such instruction.

We could try adding zero to it, but LLVM somehow inlines it immediately,
to the above same ll, even when optimizations are off, which is
frustrating.

So, we store and load it.

# Constraint RC Is Same Field As Owning RC

(CRCISFAORC)

Every struct has a ref count, and every ref (owning, constraint, shared)
counts as one towards that count.

# Declare Everything Before Defining Anything

(DEBDA)

IRegion used to have declareStruct, declareKnownSizeArray, etc.

In declareStruct, we would define a certain function. However, it
required that a struct be declared in another region, which hadn\'t
happened yet.

Instead, we made a declareStructExtraFunctions which was called
separately.

We also made a defineStructExtraFunctions, for defining the function,
which depended on the actual structs being defined.

# Elide Checks For Known Live On/Off

(ECFKLOO)

If the \--ecfkl flag is on, then we\'ll trust Catalyst and do no
generation checks where it says knownLive=true.

If the \--ecfkl flag is off, then we\'ll doublecheck what Catalyst says,
and exit with a certain error code (116) if it fails.

# Messages Are Pre-Order, With Metadata

(MAPOWM)

**REVISIT THIS, since MAP_GROWSDOWN is obsolete. perhaps we can do a
better order.**

If we have an Array\<Wing\> which contains an array of Wings \[A, B,
C\], then there are four possible layouts for a message being sent into
C:

-   WingPointerArray, (metadata), A, B, C

-   (metadata), WingPointerArray, A, B, C

-   A, B, C, (metadata), WingPointerArray

-   A, B, C, WingPointerArray, (metadata)

(There\'s actually double that, as we could serialize A, B, C as C, B, A
but that\'s irrelevant here)

(We need to have the control block next to the WingPointerArray, because
C receives a WingPointerArray\*)

Two facts:

-   We want to make use of MAP_GROWSDOWN; we want to be able to
    > serialize things at a higher address and proceed to keep writing
    > to lower addresses.

-   We need to write the WingPointerArray first, so that as we write
    > each Wing, we know where to write its address to.

That rules out some.

Also, we can\'t have:

-   A, B, C, WingPointerArray, (metadata)

as it isn\'t usable by C because given an interface pointer, it doesn\'t
know where \"after the thing\" is. So, we can\'t do this.

That leaves only one:

-   A, B, C, (metadata), WingPointerArray

To do this, we\'d need to put an \"rootMetadataBytesNeeded\" integer in
the region struct, every serialization function will adjust the
destination by that much extra and then set it to zero immediately
before anyone else can read it. That way, only the root really uses it.

There\'s one more complication. Many things (including C#) will need at
the beginning of the buffer:

-   Size

-   Start address, to subtract from all references

-   Root object offset, to start reading from

-   (we\'ll also put in a sentinel value here to look for, for
    > debugging)

So lets have another metadata at the beginning which contains those.

It will end up looking like:

-   (size, root addr, start addr), A, B, C, (start addr & size),
    > WingPointerArray

Later note: in the future, we could have a struct that inline-ly
contains the start metadata and the root object.

# Midas Process Exit Status Codes

(MPESC)

For better testing, to make sure that midas is hitting the error we
expect, we\'ll be using different process return codes.

Our return codes dont fit in well at all with the list at
[[https://www.cyberciti.biz/faq/linux-bash-exit-status-set-exit-statusin-bash/]{.underline}](https://www.cyberciti.biz/faq/linux-bash-exit-status-set-exit-statusin-bash/)
so we\'ll be using our own.

0: success in user-land

1: general error in user-land, such as a panic call

2:

14: dereferenced an invalid reference

42: general \"good result\" code for tests

73: general \"bad result\" code for tests

116: our own internal thing; catalyst said this was known live but our
doublecheck showed its not actually alive

# Representing Scope Tethering In VAST

(RSTIV)

In HGM, constraint ref parameters will be borrow references, and a lot
of constraint ref locals will be tethers.

So, besides OWN, CONSTRAINT, WEAK, should we also have BORROW and
TETHER?

## Tether

Since tethering is associated with a scope, should it be a property of a
Local?

Some musings:

-   We usually put something in the type system like this when we don\'t
    > want to accidentally convert from e.g. borrow and tether. Can we
    > load one from the other?

    -   We **can** load a borrow from a borrow local.

    -   We **can** load a borrow from a tether local.

    -   We **cannot** load a tether from a borrow local.

    -   We **cannot** load a tether from a tether local.

> So\... we can\'t load a tether from something.

-   Can anything be a tether?

    -   Locals can be borrow or tether.

    -   Params can be borrow, but **not** tether. This is because a
        > parameter goes from one scope to another, and tethers can\'t
        > travel.

It really seems like it\'s a property of a local, especially since it\'s
associated with a scope.

Extra benefit: no conversion needed to load from a local, it\'s already
a tether.

So yeah, we\'ll make it a property of Stackify.

## Borrow

Should we even have a BORROW reference? its purpose is kind of served by
knownLive.

No, let\'s keep using knownLive. BORROW exists, informally inside of
Catalyst. If we ever merge Catalyst into Midas, we can revisit whether
we represent borrows as an ownership.

It might not be a good idea, because sometimes a local constraint ref
can be guaranteed live, and sometimes not, depending on where the owning
reference is and what happens to it.

## Ok So How?

We\'ll keep the knownLive as part of the AST nodes that dereferences
memory.

Stackify will also have a keepAlive boolean, which only applies to
constraint refs.

To test, we\'ll have valestrom force keepAlive=true for locals ending in
\_\_tether.

# Modules Must Export Dependencies Themselves

(MMEDT)

Pretend we have these modules:

Module A:

struct Vec3 { x float; y float; z float; }

struct Mat3 { a Vec3; b Vec3; c Vec3; }

Module B:

> export Vec3 Direction;
>
> export Mat3 as DirectionTriple;

Module B would generate a .h file like:

> struct DirectionTriple { Direction a; Direction b; Direction c; }
>
> struct Direction { float x; float y; float z; }

So far, so good.

But when we add module C:

> export Vec3 Position;
>
> export Mat3 as PositionTriple;

**\...Midas could get confused**, because there are two export names for
Vec3. It *should* export something like this for module C:

> struct PositionTriple { Position a; Position b; Position c; }
>
> struct Position { float x; float y; float z; }

but it might accidentally export something like this:

> struct PositionTriple { **Direction** a; **Direction** b;
> **Direction** c; }
>
> struct Position { float x; float y; float z; }

because when it looks up \"the export name for Vec3\", it finds not only
\"Position\" but also \"Direction\" from module B, and it doesn\'t know
which one to use.

As a temporary workaround (search MMEDT, and
[[issue]{.underline}](https://github.com/ValeLang/Vale/issues/182)), it
arbitrarily picks the first export name found. It might lead to some C
errors (fortunately deterministic, as Valestrom\'s ordering is
deterministic).

Solution:

-   Require that one module cannot see another\'s exports, all the way
    > down to Midas.

-   Require that an export\'s dependencies must also be exported, in
    > that same module.

    -   In the above example, this is satisfied, B and C both export
        > Vec3, which their arrays depend on.

-   Require that a type only be exported once per module.

Note that we *might* also have to prefix the exported name with the
module name, so theres no collisions in the generated .h files if
someone includes both files (or even in the linker?).

Rejected solution:

-   Require that only one module (presumably the defining one) export a
    > certain type.

    -   This is impossible for e.g. Array\<imm, str\> or Array\<imm,
        > int\> or any combination of builtins.

        -   Could we instead have an automatic name generator for those
            > combinations of builtins? For example, Array\<imm, str\> →
            > StrImmArray

    -   This would make it impossible to export things defined in
        > dependencies that we don\'t have access to (unless we fork
        > them). Lots of people will be forgetting to export their
        > types.

# Suffix Adds Size Parameter

(SASP)

When vale sees an extern name ending in \_sasp, it will add extra
parameters to the C function, containing the sizes of everything that
was serialized during transit to C.

In the future, we could replace this behavior with some sort of
configuration file which specifies which functions to add that
information to. Maybe.
