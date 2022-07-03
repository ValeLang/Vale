# Templar\'s Purpose

Templar has four jobs:

1.  Assign types to all expressions

2.  Split lambdas out to be their own functions and their own structs

3.  Resolve overloads

4.  Monomorphize all templates (this will be moved to Hammer one day)

5.  Templar also figures out whether locals are addressibles or
    > references.

    -   An **addressible local** is one we can take the address of.

        -   Hammer later lowers this to an inl Box, which works well for
            > JVM and JS, and is zero cost in LLVM.

    -   A **reference local** is one we never take the address of.

    -   It does this by looking to see if anyone ever captures it in a
        > lambda.

Templar takes in the Astronomer output (suffix begins with A) and
produces the Templar output (suffix begins with T).

## Long Term Direction

Templar will soon not be monomorphizing anything. It will produce an AST
that\'s similar to a Java program, in that it templates are actually
generics. Metaprogramming directives will be preserved.

Hammer will be the one to monomorphize the generics into templates, and
monomorphize all metaprogramming.

We\'ll also make it query-based.

-   Look for any lazy val, those might need to be made thread-safe.

# Profiling

1.  Open the project in IntelliJ IDEA Ultimate (will need a license, ask
    > Verdagon)

2.  open
    > Valestrom/IntegrationTests/test/net/verdagon/vale/benchmark/Benchmark.scala

3.  right click, Run \'Benchmark\' (thisll take a couple minutes to
    > finish)

4.  you should see a button in the top right ish
    > area:![](media/image2.png){width="6.5in"
    > height="1.2777777777777777in"}specifically this one:
    > ![](media/image1.png){width="0.875in"
    > height="0.6041666666666666in"}

5.  click its down arrow

6.  click Run \'Benchmark\' with \'CPU Profiler\' (runs it again, itll
    > take a couple minutes again)

# Destroy Ignored Patterns Right Away

(DIPRA)

When we do:

> Spaceship(\_, a) = makeShip();

we want to throw away that \_\'d value right now, instead of putting it
in a local to be destroyed later.

# Defer

The Defer node will do an expression, and then another expression
slightly delayed.

\"Slightly delayed\" means after the nearest containing:

-   function call (interface, extern, function pointer, construct array)

    -   In a perfect world, we could execute these right after we move
        > the arguments into the new stack frame, but before the
        > function begins executing.

-   mutate

    -   mutating reference local

    -   mutating addressible local

    -   mutating reference member

    -   mutating addressible member

    -   mutating unknown size array

    -   mutating known size array

-   destructure

-   let, let-and-lend

-   PackE2

-   TupleE2

-   ArraySequenceE2

-   Construct2

-   CheckRefCount2

-   Discard2

-   Block2

It will also make sure theyre calculated *before* a containing return.
We must execute deferreds before a return (MEDBR) because otherwise the
return will shoot us out of the function before the deferreds can even
happen.

However, it doesn\'t include:

-   load

    -   from reference local

    -   from addressible local

    -   from reference member

    -   from addressible member

    -   from unknown size array

    -   from known size array

The reason for this is to support this kind of expression:

> struct Wand { charges: Int; }
>
> struct Wizard { wand: Wand; }
>
> fn main() {
>
> = Wizard(Wand(10)).wand.charges;
>
> }

In this, because we don\'t include load, it\'s happening in this order:

-   Store Wizard(Wand(10)) into a temporary local

-   Borrow the .wand into a register

-   From the register, copy the .charges

-   Destroy the temporary local

If we instead did include load, it would happen in this order:

-   Store Wizard(Wand(10)) into a temporary local

-   Borrow the .wand into a register

-   Destroy the temporary

-   From the register, copy the .charges

which would halt, because there\'s still a borrow reference in that
register.

TODO: we need to figure out what to do if there are deferreds active
when we return from a function. This probably wont happen since we have
a \_\_result variable, but it theoretically could. There\'s an assert
for it in FunctionHammer.

# Struct Templar

TemplateArgsLayer

MiddleLayer:

\- puts stuff into environment; makes a local environment

CoreLayer:

\- Makes structs, makes interfaces. That\'s it.

See Infer Templar doc too

# Drop, Free, Shared, Owned (DFSO)

Every kind has a drop method. It\'s as if we did #\[derive(Drop)\] on
every struct, interface, and array.

We\'ll have a check in templar making sure we don\'t have any
user-defined drop functions for anything immutable.

Later on, let\'s detect which ones were actually automatically
generated. Those can be elided out when we compile to JS, JVM, etc.

Later, we can have a keyword, like #\[derive(!Drop)\]

Options:

A.  Generate late, at call time. This is doable, but requires special
    > logic for finding destructors, some sort of fall-back function.

B.  Generate at definition time. This gets into an interesting
    > conundrum, because of a circular dependency:

    -   To make the struct\'s environment, we need the drop function
        > that will go in it.

    -   To know whether to make a drop function for it, we need to first
        > check if there already exists a user-defined drop function.

    -   To know whether there\'s a user-defined drop function, we need
        > to ask the overload templar and give it a coord with this
        > struct.

    -   To get a coord with this struct, we need it to finish defining
        > it and making an environment.

C.  Just after definition, look for an existing one, and if not,
    > generate one and put it in a specific destructors list in the
    > temputs. (We used to do this)

D.  Instead of using overloadtemplar, we can have the astronomer figure
    > out whether there\'s a drop that takes this generic.

E.  Specify via derive. Pretty simple, rather annoying though.

F.  Require manually specifying (temporarily)

D and E are likely the best ones. The decision comes down to whether we
want any magic.

However, if we define a different drop() for a class, then our default
one won\'t be generated. **I don\'t like this, it seems too magical.**
Let\'s keep thinking.

## Separate Drop and Free Functions (SDFF)

The difference:

-   free will only:

    -   Destroy instances

    -   Discard references

    -   Call other free() functions.

-   drop does anything else, such as unregistering observers, etc.

The nice thing about separate drop and free functions is that we can
elide any free calls when:

-   Compiled to JS, Java, Swift, C#, anything managed

-   In Vivem

-   In a bump-allocated type-stable region (or, well, we defer the free
    > calls, hoping we will never need to run them)

This difference doesn\'t necessarily need to be exposed to the user, see
ADSDF.

Though, there is another distinction we need. Currently, an immutable\'s
drop function is only called when its refcount reaches zero. However,
the user can manually call it (oops) and the array drop method also
manually calls drop on its elements (oops again).

We need those to call a function that just discards it. We can have a
separate function, perhaps called free, which deallocates.

## Automatically Detect Simplifiable Drop Functions (ADSDF)

Here, the user only needs to know about one function: drop(). They would
likely automatically derive(drop) on everything.

Later on, we detect any hierarchies for which we\'ve deeply derived
drop, in other words, where drop is never doing anything interesting,
just deallocating memory. Then, rename those to free() and elide them.

But, we face a classic static analysis problem: if there are any
widely-used interfaces, such as IFunction1, even *one* subclass that has
an \"interesting drop\" can cause *every* struct that indirectly
contains an IFunction1 to have a drop method. But\... in practice,
that\'s no different than if the user manually did it, i suppose.

## Need Separate IDrop and Drop Names (NSIDN)

We made it automatically generate drop functions for everything.

However, automatically generating a drop function for a struct which
implements a few interfaces is challenging.

> interface IShip {
>
> // implicit: fn drop(self)
>
> }
>
> interface IExplosive {
>
> // implicit: fn drop(self)
>
> }
>
> struct Missile impl IShip impl IExplosive {
>
> // implicit: fn drop(self) ← problem
>
> }

The problem here is that we need to signal that that drop function
overrides IExplosive\'s drop and IShip\'s drop.

It\'s also tricky because:

-   To generate the right impl IWhatever, we need to know its ancestors.

-   To know its ancestors, we run the solver and stamp all parent impls.

-   This means a temporary structref is getting used by the solver (and
    > who knows who else) before the struct is done instantiating.

So instead, we\'ll have two different rules:

-   Every type (struct, interface, array, primitive, everything) has a
    > \"drop\" method.

-   Every interface and impl have a \"vdrop\" method.

-   An interface\'s \"drop\" calls its own abstract \"vdrop\".

-   An impl\'s \"vdrop\" calls its concrete type\'s \"drop\" method.

## How We Derive Drop (HWDD)

There are some macros added automatically:

-   #DeriveStructDrop is added to every struct

-   #DeriveInterfaceDrop is added to every interface

-   #DeriveImplDrop is added to every impl

We can opt out of those by annotating e.g. #!DeriveStructDrop:

> struct Muta #!DeriveStructDrop { }

That means that we can have other destructors, such as the commit() in
the RAII article, but mostly its so we can define our own custom drop
function:

> fn drop(m \^Muta) void {
>
> println(\"Destroying!\");
>
> Muta() = m;
>
> }

Whether we expose an explicit #\[derive(Drop)\] or #DeriveDrop(MyStruct)
or whatever, we\'ll have something like it in the compiler.

It would basically be declaring a:

> fn drop(this MyStruct impl IWhatev impl IOther)
> extern(\"dropGenerator\");

in the same environment as the struct.

## The Immutable Free Anomaly (TIFA)

## Generate Destructors For All Kinds (GDFAK)

We ran into an interesting bug\...

> fn main(moo &Moo) int export {
>
> moo.hp
>
> }

This never called the destructor, so it was never generated.

## Droppable Option, Arrays, and other Containers (DOAOC)

Do we want to define a default drop for arrays?

-   **Always drop:** and also require that the contents have a drop
    > method.

-   **Explicit drop:** require they call drop(array, some destroying
    > lambda)

-   **Intelligent drop:** Do A when the contents has a zero-arg drop
    > method, otherwise do B.

How would we do intelligent drop?

**Approach A:** Some sort of conditionally enabled function?

> interface Opt\<T\> {
>
> fn drop\<T\>(this &Opt\<T\>) if Prot(\"drop\", (), \_) abstract;
>
> fn drop\<T, F\>(this Opt\<T\>, elemDrop F) {
>
> this match {
>
> None() {}
>
> Some(val) { drop(val) }
>
> }
>
> }
>
> if dropFunc = Prot(\"drop\", (), \_) {
>
> fn drop\<T, F\>(this Opt\<T\>) { dropFunc(this) }
>
> }
>
> }

**Approach B: Late-Checked Default Parameters**

> interface Opt\<T\> {
>
> fn drop\<T, F\>(this Opt\<T\>, elemDrop F = fn drop(T)void) {
>
> this match {
>
> None() {}
>
> Some(val) { drop(val) }
>
> }
>
> }
>
> }

We evaluate that = fn drop(T)void at the call-site. If that doesn\'t
exist, then we throw an error there.

(We can\'t evaluate that = fn drop(T)void when we first declare
Opt\<MyStruct\> because it might not exist, the user might only want to
use lambdas for that)

We may run into situations where a function calls a function calls a
function and all of them do this.

**Approach C: Structural Interfaces**

Approach B is kind of an interpretation of structural interfaces. Kind
of. If we have those, they might reuse them to solve this.

**Approach D: Automatic Impl\'ing**

We can have a zero-arg Drop trait that is automatically impl\'d if all
members impl it.

Though, interfaces might run into a problem here. We\'d have to
explicitly inherit Drop in those.

**Approach E: Make Derive Drop Smart**

We could have the derive(drop) macro be smart, and see if there is any
drop() function available. If not, throw an error.

**Comparison**

B seems the most promising, but lets wait a bit before we decide to add
that kind of mechanism.

## Possible Syntax

### A: Explicit Hash Call

#DeriveStructDrop

### B: Use Tildes

If we have any \~ in any fields, then we automatically derive drop.

But what do we do for empty structs?

### C: Opt Out

Can say #!DeriveStructDrop to signal we *don\'t* want that macro called.

### Conclusion

We\'ll go with A for now, and C later.

## Drop\'s Argument

Does drop receive an owning yonder, owning inline, or some sort of
special thing?

It could receive a doomed reference, which is basically like a yonder
own, but it can point to something inline in another object.

It promises we\'ll destroy it in this function.

Or maybe, the caller has to promise to destroy it after the function?

For now, we don\'t even have inlines, so just make it accept a yon own
reference.

# No Destructor For Empty Tuple Type (NDFETT)

Every destructor returns \[\].

So what does \[\]\'s destructor return?

To solve that conundrum, we just don\'t have a destructor for it.

**Note from later:** This was repealed. We now have a specific VoidT
kind.

# Need Interface Identifying Rune In Impl (NIIRII)

We had a weird bug where if a struct implemented two interfaces, two
vdrops would be generated, which is good\... but they collided because
they had the same signatures.

To solve it, we made vdrop have the interface rune as another
identifying rune.
