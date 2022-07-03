[[Closures Need Environments]{.underline}](#closures-need-environments)

> [[Structs Also Need
> Environments]{.underline}](#structs-also-need-environments)
>
> [[Environments and Members Are
> Graphs]{.underline}](#environments-and-members-are-graphs)
>
> [[Mutual Functions]{.underline}](#mutual-functions)
>
> [[Mutual Struct and
> Function]{.underline}](#mutual-struct-and-function)
>
> [[Function and Environment]{.underline}](#function-and-environment)
>
> [[Closure\'s Struct and Function Must See Each
> Other]{.underline}](#closures-struct-and-function-must-see-each-other)
>
> [[Moral of the Story]{.underline}](#moral-of-the-story)
>
> [[Solution: Structs and Functions Have
> Environments]{.underline}](#solution-structs-and-functions-have-environments)
>
> [[Alternative 1: Put Environments in the StructDef /
> Function2]{.underline}](#alternative-1-put-environments-in-the-structdef-function2)
>
> [[Alternative 2: \[Struct, Environment\]
> Kind]{.underline}](#alternative-2-struct-environment-kind)
>
> [[Are Environments Necessarily
> State?]{.underline}](#are-environments-necessarily-state)

[[Representing Overload Sets]{.underline}](#representing-overload-sets)

> [[Calling Ordinary
> Overloads]{.underline}](#calling-ordinary-overloads)
>
> [[Passing Overloads Into
> Runes]{.underline}](#passing-overloads-into-runes)
>
> [[Solution: \[Name, Environment\]
> Kind]{.underline}](#solution-name-environment-kind)
>
> [[Optimization for Calling Ordinary
> Overloads]{.underline}](#optimization-for-calling-ordinary-overloads)

[[Unified Solution]{.underline}](#unified-solution)

> [[Using SAFHE for ROS]{.underline}](#using-safhe-for-ros)
>
> [[\[Struct, Environment\] for
> ROS]{.underline}](#struct-environment-for-ros)
>
> [[\[Name, Environment\] for
> CNE]{.underline}](#name-environment-for-cne)
>
> [[\[Struct, Environment, Function Map\] for
> ROS]{.underline}](#struct-environment-function-map-for-ros)

# Closures Need Environments

CNE

Let\'s say we have a simple function which calls its argument:

> fn huzzah(f: #F) {
>
> f();
>
> }

And now we call it with a closure:

> fn main() {
>
> huzzah({ print(\_); })
>
> }

There\'s three ways we can think of this:

-   That { print(\_); } evaluates to some sort of special kind that has
    > both the \_\_Closure:main:lam1 struct plus the main:lam1
    > FunctionS.

-   That { print(\_); } sticks a \_\_call into main\'s environment and
    > evaluates to a regular struct kind for \_\_Closure:main:lam1.

-   That { print(\_); } sticks a \_\_call into the environment and
    > evaluates to some sort of special kind that has a snapshot of
    > main\'s environment at the time, and the struct for
    > \_\_Closure:main:lam1.

However, those first two won\'t work. Imagine this case:

> namespace FlamingJustice {
>
> fn doublePrint:#X(x: #X) {
>
> print(&x);
>
> print(&x);
>
> }
>
> fn getThing() {
>
> = { doublePrint(\_); };
>
> }
>
> }
>
> fn main() {
>
> FlamingJustice.getThing()();
>
> }

The first two options threw away the original environment for
getThing:lam1\'s \_\_call! That means we\'ve completely lost
doublePrint, it\'s nowhere in memory.

Therefore, { doublePrint(\_); } **must** return something that **at
least** has its environment, even if indirectly.

(Note, it doesn\'t just have to have its environment, it could also have
the \_\_call FunctionS so we don\'t have to go digging for it in the
environment. Probably not worth the complexity though.)

## Structs Also Need Environments

Let\'s say we have a namespace with a templated struct that uses
functions from that namespace:

> namespace FlamingJustice {
>
> struct Knight:#T {
>
> name: Str;
>
> weapon: #T;
>
> }
>
> const prefix = \"Hello, \";
>
> fn print:#T(this: &Knight:#T) {
>
> print(prefix + this.name);
>
> }
>
> }

Now lets say we pass it to a templated function:

> fn doublePrint:#X(x: #X) Void
>
> where { #X has { fn print(this)Void; } }
>
> {
>
> x.print();
>
> x.print();
>
> }
>
> fn myFunc(m: FlamingJustice\'Knight:Sword) {
>
> doublePrint(m);
>
> }

what is #X here?

It has to contain the environment somehow, **at least indirectly**, so
that when we stamp that print(:Knight:Int) it can know prefix.

It\'s not only closures that face this problem; these are the same basic
problem. main:lam1 and this FlamingJustice\'Knight are serving the same
role in these examples.

## Environments and Members Are Graphs

EAMAG

Here are some examples:

### Mutual Functions 

Imagine a namespace which has:

> fn shoot(this: &Marine) {
>
> \...
>
> run(this);
>
> \...
>
> }
>
> fn run(this: &Marine) {
>
> \...
>
> shoot(this);
>
> \...
>
> }

These functions need each other in their environment.

### Mutual Struct and Function

Imagine a namespace which has:

> struct Marine {
>
> hp: Int;
>
> }
>
> fn shoot(this: &Marine) { \... }

For shoot to be compiled, it needs Marine in its environment.

And, because we\'re going with the SAFHE approach for closures, Marine
needs to have shoot in its environment.

### Function and Environment

This problem exists even for a simple function in an environment!

> fn shoot(this: &Marine) { \... }

This function needs to know its environment so that it can eventually be
parsed. But the environment needs to contain this function!

Since we\'re in a functional language, we can\'t have cyclical
references.

Let\'s make the environment contain the declarations (such as signature
or structref). When something in the environment is called, we can
lazily compile it.

To use a declaration to lazily compile, we need to get at its
environment from the templata.

We would be tempted to put the environment in the templata but then
we\'d have a cyclical reference between the environment and its
templatas.

So instead, we\'ll put the environment paths into the templatas. The
temputs will have a map of path to environment.

### Closure\'s Struct and Function Must See Each Other

CSFMSEO

If we have a closure:

> fn main() {
>
> { \_ + \_ }(4, 5);
>
> }

We\'ll need to map from that StructRef2 to its environment which
contains some sort of reference to the function. However, a Function2
would have a reference to that struct, since its its first argument. We
have a circular dependency!

### Moral of the Story

Environments\' members are a graph. They often need to know about each
other in interesting ways.

## Solution: Structs and Functions Have Environments

(also known as SAFHE)

Fundamental principle: In the temputs, we\'ll have a Map\<StructRef,
Environment\> and a Map\<FunctionRef, Environment\>. A given StructRef
or FunctionRef **must** have an entry in the mapping before someone
tries to use them.

Let\'s say we have an ordinary closure:

> fn main() {
>
> { println(\"Hi!\"); }();
>
> }

We would compile it like this:

-   Declare the struct to get a StructRef. Note that before we can use
    > this StructRef yet, we need to map its environment, which should
    > contain the function, in some form.

-   Make environment E which contains the StructRef and the FunctionS in
    > it.

-   Associate the StructRef with environment E in the temputs.

-   Compile the StructDef.

-   Eagerly compile the FunctionS.

-   Whenever someone might call the StructRef, they\'ll look into its
    > environment and find the FunctionS, and attempt to compile it, and
    > they\'ll see it\'s already compiled, which is fine.

Let\'s say we have a templated closure:

> fn main() {
>
> { println(\_); }(\"Hi!\");
>
> }

We would compile it like this:

-   Declare the struct to get a StructRef. Note that before we can use
    > this StructRef yet, we need to map its environment, which contains
    > the FunctionRef. We don\'t have that yet, so we can\'t map its
    > environment, so we can\'t use this StructRef

-   Make temporary environment S which contains the StructRef **and**
    > the FunctionS in it.

-   Map that StructRef to environment S in the temputs.

-   Compile the StructDef.

-   Whenever we call the closure, thereby manifesting the function, grab
    > the environment out of the temputs. We can actually compile the
    > entire thing, since the StructRef and the FunctionS are in the
    > environment.

### Alternative 1: Put Environments in the StructDef / Function2

This is very similar to the accepted solution. However, this means that
we associate the struct with its environment later in the process\...
specifically, after we make all the members. Compiling the members
involves stamping templates which involves stamping functions, which
could indirectly cause us to try and call a function on this struct\...
before we know its environment.

For example:

> struct MyStruct\<T\> {
>
> next: OtherStruct\<MyStruct\<T\>\>;
>
> value: T;
>
> }

We won\'t give MyStruct\<Int\> an environment until *after* we\'ve
compiled all the members. However, OtherStruct might have a function
that calls MyStruct\<Int\>\'s print() or something.

### Alternative 2: \[Struct, Environment\] Kind

A special kind which contains the environment.

We can have a kind called EnvironedKind.

> case class EnvironedKind(
>
> env: LocalEnvironment,
>
> innerKind: IKind)

So, for the templated closure situation:

> namespace FlamingJustice {
>
> fn doublePrint:#X(x: #X) {
>
> print(&x);
>
> print(&x);
>
> }
>
> fn getThing() {
>
> = { doublePrint(\_); };
>
> }
>
> }
>
> fn main() {
>
> FlamingJustice.getThing()();
>
> }

the { doublePrint(\_); } would evaluate to an EnvironedKind(that env,
main:lam1).

## Are Environments Necessarily State?

AENS

Variables are being introduced and destroyed and moved all the time in a
function. That is definitely \"function state\".

We also have the \"environment\", which contains all the templatas. Is
that state? Does that change?

In a function, at any time, we can introduce a templata which will be
available to all the subsequent expressions, such as by let :&#T = &x;,
which suggests the environment is state. **However,** we can look
forward to find all of these in the function ahead of time, and just
have them in the original environment.

We can also introduce a lambda\'s function at any time:

> fn main() {
>
> \...
>
> let f = { \_ + \_ };
>
> \...
>
> }

once we get to that line, the \_\_main:lam1 function is in the
environment. This suggests the environment is state.

**However,** same thing, we can look forward to find all of these in the
function ahead of time, and just have them in the original environment.

So no, the environments aren\'t necessarily state, if we maneuver just
right. It\'s possible we might someday find a reason to make them state
though.

# Representing Overload Sets

ROS

This problems comes in two forms.

## Calling Ordinary Overloads

Let\'s say we have a bunch of print functions:

> fn print(i: Int) { \... }
>
> fn print(b: Bool) { \... }
>
> fn print(s: Str) { \... }

A complexity arises with this simple function call:

> fn main() {
>
> print(true);
>
> }

and that call is parsed as:

> FunctionCallSE(GlobalLoadSE(print),PackSE(List(BoolLiteral(true))))

What templata is produced by that GlobalLoadSE?

It needs to be something that simultaneously represents all the print
functions in scope.

## Passing Overloads Into Runes

POFIR

Let\'s say we have a function that does forEach over a sequence:

> fn forEach:#F(seq \[members\...: #M\...\], f: #F) {
>
> {\... f(m\...); }
>
> }

And we call it with print:

> fn main() {
>
> forEach(\[1, 2, true, 4\], print);
>
> // or:
>
> forEach:print(\[1, 2, true, 4\]);
>
> }

What is #F?

## Solution: \[Name, Environment\] Kind

A kind that\'s an environment and a name:

> case class OverloadSet(
>
> env: LocalEnvironment,
>
> name: String)

If it makes us feel weird to not have anything tangible underneath this
kind, we can throw in a StructRef that points to void too:

> case class OverloadSet(
>
> env: LocalEnvironment,
>
> name: String,
>
> voidStructRef: StructRef)

### Optimization for Calling Ordinary Overloads

FunctionCallSE\'s handling can specifically look for GlobalLoadSEs and
make sure to evaluate the arguments first, then use it to resolve the
overload.

# Unified Solution

\...has not been found. The two needs are too different. This section
will talk about ideas on how to use one problem\'s solutions for the
other, and how they fail.

## Using SAFHE for ROS

(Using \"Structs And Functions Have Environments\" for \"Representing
Overload Sets\")

The struct has an environment. This works well for CNE: we can just look
up the structs\' methods in that environment.

However, for ROS, when we attempt to call \_\_call on that StructRef, it
doesn\'t work.

-   We need that to reach into the original environment and grab all the
    > print methods. But how did we know to look for print? We were
    > looking for \_\_call! We don\'t know that we need to look for all
    > \"print\". We *could* augment the environment, find all the print
    > methods, and add entries to make \_\_call point at them. However,
    > even if we could find them\...

-   We we would attempt to send this StructRef in as the first
    > argument\... but these print methods don\'t accept this struct as
    > their first argument!

-   Besides, there are already other \_\_call methods in the
    > environment! Things would get very confusing. There\'s no way to
    > tell them apart, because none of them take our special struct as
    > their first argument!

## \[Struct, Environment\] for ROS

We want to represent the print overload set with a \[Struct,
Environment\] thing. However, we run into the same problem that SAFHE
did.

When we attempt to call \_\_call on that thing:

-   We we would attempt to send this StructRef in as the first
    > argument\... but these print methods don\'t accept this struct as
    > their first argument!

-   Besides, there are already other \_\_call methods in the
    > environment! Things would get very confusing. There\'s no way to
    > tell them apart, because none of them take our special struct as
    > their first argument!

## \[Name, Environment\] for CNE

So we have a special kind that has a name and environment. That solves
ROS nicely. However, it doesn\'t help CNE because it\'s all about
passing closures, and closures must have a struct to hold their
captures!

## \[Struct, Environment, Function Map\] for ROS

In this new approach, we\'re augmenting the \[Struct, Environment\]
approach with a Map\<String, FunctionS\> to have \_\_call entries
pointing at print.

> case class ContractObject(
>
> environment: Environment,
>
> functions: Map\[String, List\[FunctionS\]\],
>
> innerKind: IKind)

This doesn\'t work well, for the same reason:

-   We we would attempt to send this StructRef in as the first
    > argument\... but these print methods don\'t accept this struct as
    > their first argument!
