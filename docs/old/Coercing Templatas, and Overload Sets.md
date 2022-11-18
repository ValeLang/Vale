# Can Coerce Kinds To Coords (CCKTC)

Let\'s say we have Map:(Int, &Marine). Knowing the definition of Map,
those parameters are obviously coords. However, it\'s not obvious when
you look at Map:(Int, Marine). We could be passing in a kind there. The
problem is that Marine is ambiguous; it could either be an owning
reference or a kind. We knew that ambiguity would bite us one day\...
today is that day.

We would also like it if, when the user gives a &Marine to a template
param expecting a kind, it would give us an error.

There are three options to deal with this:

-   A: Leave it ambiguous. Alongside the KindTemplata and CoordTemplata,
    > we have the KindOrCoordTemplata. It will conform to whatever.
    > Three sub-solutions:

    -   A1: Any rune can contain a KindOrCoordTemplata; it\'s a templata
        > like any other.

    -   A2: As soon as it enters the rule system it\'s coerced. If we
        > can\'t figure out what to coerce it to, we coerce it to a
        > **coord**.

    -   A3: As soon as it enters the rule system it\'s coerced. If we
        > can\'t figure out what to coerce it to, we coerce it to a
        > **kind**.

-   B: When we\'re in the outside world (everything outside rules) we
    > only let the user think in terms of coords. Just like how patterns
    > work. Saying Marine like that gives us a coord. Two sub-solutions:

    -   B1: Whenever a template is expecting a kind, we coerce it down
        > to a kind. In other words, we always allow coercing a coord to
        > a kind.

    -   B2: Whenever a template is expecting a kind, we call the Kind
        > cfn. Kind:Marine would give us the kind.

-   C: We by default assume it\'s a kind, and don\'t ascribe any extra
    > meaning to it. Later on, we can coerce it upwards to a coord.

-   D: Have template parameters be typed, so during a call, we\'ll
    > always know what we\'re *trying* to get. For example, when we say
    > Set:Marine, we first see Set, and know that it takes in a coord.
    > So, when resolving the name Marine, we\'ll know to resolve it *as
    > a coord*. Note, this requires we statically type every template.

We can\'t go with B because the user can\'t get away from thinking about
kinds in the outside world. What if we had a template that took in a
kind, like an indexing datastructure that took in a struct merely so it
could grab some statics out of it? Or what if we give it an enum, so it
can enumerate through all the entries? Or maybe a graph datastructure
that just wanted to know the kind of node, so it could manage its own
owning and borrows.

The rest are valid, but we\'re going with D, because it catches problems
sooner than the rest of them.

To pull off D, we\'ll need to statically type every template; all of its
rules. Luckily, this requires very few annotations.

For example, in:

> fn Array(n: Int, x: #F) #T
>
> where(#F.kind like IFunction\<(Int), #T\>) {
>
> = x + x;
>
> }

we have no annotations, yet we know that #F and #T are both coords
because they\'re in parameter and return slots.

Another example:

> Ref#C\[\_, \_, \_, #X\]
>
> #X = MyStruct

We know that #X has to be a kind because it\'s in the kind slot of the
Ref components.

I suspect we\'ll very rarely need to specify the type for something.

## Need Types on Every Rule and Templex (NTERT)

For the evaluation part of the inferer, we need to know what type is
expected when we hand it in to a call.

For example, for Kind#X = MyInterface:Int, how does the InferEvaluator
know whether Int is a kind or a coord there?

We\'ll need to use the parameter types from MyInterface.

Which means we need to see MyInterface to properly statically type this.
This means this has to happen sometime after scout, maybe even in
templar, because that\'s when we have access to the rest of the world.

# Can Use Lambdas for Interfaces

How does this lambda become an IThing?

> fn moo(f: IThing) { \... }
>
> fn main() {
>
> let x = 4;
>
> moo({ print(\_ + x) })
>
> }

## A. Implement IFunction Automatically

Make that lambda implement IFunction automatically.

Can\'t do this, because can\'t templated-ly implement an interface;
can\'t do this:

> struct MyThing { }
>
> impl\<#T\> MyThing for IFunction1\<mut, #T, #T\>;

One reason is that we need to be able to know all of a struct\'s
ancestors from its environment, and we won\'t know this one\'s because
any callsite can add another ancestor.

## B. Become the Receiver Type

Require that we know beforehand what it ordinary interface will
implement specifically. In this case, it will see that it\'s about to be
put into an IThing, and so will implement that interface. In this way,
it\'s bit amorphous, like a pack.

Once it knows the type, it can have an impl for it. This even works for
polymorphic lambdas:

> interface IForeacher {
>
> fn iterate\<#X\>(x: #X)Void;
>
> }
>
> fn foreach\<#T\>(things \[\...#T\], func: IForeacher) { \... }
>
> fn main() {
>
> foreach({ print(\_) })
>
> }

Note how it\'s an ordinary interface, but with a templated function. It
can also be templated:

> interface IForeacher\<#R\> {
>
> fn iterate\<#X\>(x: #X)#R;
>
> }
>
> fn foreach\<#T\>(things \[\...#T\], func: IForeacher\<Bool\>) { \... }
>
> fn main() {
>
> foreach({ print(\_); = true; })
>
> }

This only works because the receiver knows the exact type it wants.

We might be able to make the claim that this would work if, between the
two, they know what they want\... theoretically we didn\'t need to
specify the Bool, and could have done:

> interface IForeacher\<#R\> {
>
> fn iterate\<#X\>(x: #X)#R;
>
> }
>
> fn foreach\<#T, #R\>(things \[\...#T\], func: IForeacher\<#R\>) { \...
> }
>
> fn main() {
>
> foreach({ print(\_); = true; })
>
> }

and it would figure out #R from the = true; there. It\'s hard to
determine what the resulting type would be, without plugging in a type.
Maybe we can try plugging in a NotInfered, and see if we get something
out (like Bool in this case)?

## C. Be a Template

The problem is mitigated because we can make the receiver a template:

> fn moo(f: #F \< IThing) { \... }
>
> fn main() {
>
> let x = 4;
>
> moo({ print(\_ + x) })
>
> }

## D. Make New Subclass

We can definitely do this by explicitly making a subclass:

> moo(IThing({ print(\_ + x) }))

but it would be unfortunate to require that.

We can of course just use a pack, and it will become an IThing:

> moo(({ print(\_ + x) }))

Another idea is to have the callee say \"implicit\" so that we\'ll try
to call the IThing constructor with whatever argument they just passed
in.

> fn moo(f: implicit IThing) { \... }
>
> fn main() {
>
> let x = 4;
>
> moo({ print(\_ + x) })
>
> }

This will call the IThing constructor with whatever\'s given.

For now we\'ll go with C and D, minus the implicit keyword.

# Can Coerce Functions to Structs (CCFTS)

Note: We don't have this yet, and it's unsure if/how this might overlap with OverloadSetT.

In other words, every Function Has A Functor

Remember how closures can contain members, and are secretly structs
under the hood:

> fn main() {
>
> let a = 7;
>
> let x = {\[a\](b) print(a + b);}
>
> x(8);
>
> }

Also consider how C++ has functors, and a function can become a functor
easily:

> template\<typename F\>
>
> void callThing(int x, F&& functor) {
>
> functor(x);
>
> }
>
> void print(int n) {
>
> std::cout \<\< n \<\< std::endl;
>
> }
>
> void main() {
>
> callThing(7, print);
>
> }

We can do the same thing: make it so when we use this function, we make
a struct instead, and put a \_\_call into its environment. However, this
\_\_call isn\'t exactly like the original print function; it takes in
that special struct as its first argument.

Keep in mind that a StructDef contains its environment, which should
have the \_\_call in it.

All print functions *could* share the same struct and be different
overloads on it. However, that means that *all* functions named print
will be in that StructDef\'s environment. Not sure if we want that.
It\'s probably best to either:

-   Make the struct on the fly.

-   Make each overload have its own struct.

# Concepts

We want to do something like this:

> fn Array(n: Int, generator: #F) Array\<#T\>
>
> where (#F like IFunction\<mut, Int, #T\>) {
>
> \...
>
> }

That #F like IFunction\<mut, Int, #T\> means that #F doesn\'t
necessarily need to implement that interface, but it should have all the
same members. IOW, structural subtyping instead of nominal subtyping.

# Astronomer

(also Must Delay Rule Typing Calls Until Templar, MDRTCUT)

To take some logic out of the templar, we have a stage before it which
types all the functions. It will figure out if theyre just ordinary
functions, or template functions, and if template functions, it will
figure out what the templates take in and what they return. In other
words, it runs the Rule Typer on the functions. It figures out every
rune in every function, and especially the hard part of figuring out
whether e.g. \"Int\" is the rune int or the coord int.

It also does it for closures. (Note from later: it doesn't do that)

It also runs it on Lets, to figure out their rules, because why not. (Note from later: it doesn't do that)

However, it still can\'t run the rule typer on the doStuff line here:

> fn main() {
>
> let x = moo();
>
> let y = boo();
>
> doStuff\<Int, MyThing:Bork\>(x, y);
>
> }

because it doesn\'t know what doStuff overload we\'re calling, because
we don\'t know the types of x and y\... which means we don\'t know the
rules to try to comply to, which means we dont know whether Int is a
kind or coord.

That\'s fine, we can save that for the templar.
