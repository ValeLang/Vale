## Templatas Must Remember Environment

(TMRE)

An environment will contain raw functions and structs and interfaces.
When we pull things out of those environments, we need to remember the
environment it came from, so we can ask it for various things.

We pair those two things together (the containing environment, and the
raw function/struct/interface) into a templata.

One reason we need this is because when we want to call a method on a
struct, like this:

> spaceship = Spaceships:Firefly(\"Serenity\");
>
> fly(spaceship); // or spaceship.fly();

we need to know where to look for fly; we look in the environments of
every argument.

Another reason is for closures; when we create a closure struct, its
\_\_call has to be somewhere, we put it in its environment.

## Ordinary Functions Can Be Templates

(OFCBT)

Let\'s say we have this:

> struct Map\<Key Ref, Value Ref\> {
>
> struct Entry {
>
> key Key;
>
> value Value;
>
> }
>
> \...
>
> }

and we construct it like this:

> Map(
>
> Map:Entry(\"Serenity\", \"Firefly\"),
>
> Map:Entry(\"Hyperion\", \"Battlecruiser\"))

Map:Entry is a template.

## Need To Know Parent Rules and Runes

(NTKPRR)

Let\'s say we have this:

> struct Map\<Key Ref, Value Ref\> {
>
> struct Entry {
>
> key Key;
>
> value Value;
>
> }
>
> \...
>
> }

and we construct it like this:

> Map(
>
> Map:Entry(\"Serenity\", \"Firefly\"),
>
> Map:Entry(\"Hyperion\", \"Battlecruiser\"))

we want to infer from that Map:Entry call that Key is Str and Value is
Str.

However, this is quite difficult.

Let\'s look at the implicit constructor:

> struct Map\<Key Ref, Value Ref\>
>
> where {
>
> Key = Ref(\_, Kind(imm))
>
> } {
>
> struct Entry {
>
> key Key;
>
> value Value;
>
> fn Entry(this Map\<Key, Value\>:Entry, key Key, value Value) {\
> this.key = key;
>
> this.value = value;
>
> = this;
>
> }
>
> }
>
> \...
>
> }

If the compiler just receives that

> fn Entry(this Map\<Key, Value\>:Entry, key Key, value Value) {\
> this.key = key;
>
> this.value = value;
>
> = this;
>
> }

alone, then\... are Key and Value pre existing types in the environment,
or are they runes? We need to see them as runes. For this reason, the
Scout stage will keep track of every declared rune anywhere in our
parents (in this case, Map is the parent).

The next problem is that, looking at that function alone, we don\'t know
the rules that govern Key and Value; we don\'t know that Key can only be
immutable.

This means that when we call Map:Entry, **we need to know the rules of
every parent**, so we can incorporate them at solve time.

So, because of this, we need to remember the parents\' rules, from the
time we get the struct or interface to the time we solve it. For
example, if we say IFunction1\<mut, Int, Int\>, we:

1.  Look up the IFunction1, and remember it.

2.  Look up the mut and Int and Int, and remember those.

3.  Call the template we remembered from step 1, with the arguments from
    > step 2.

Between steps 1 and 3, we need to remember where IFunction1 came from,
and its parents\' rules.

For that reason, when we pull the Entry function from the environment,
we need to remember the containing struct it came from.

Now, we need to make sure that the Entry function gets its correct name
(Map\<Str, Str\>:Entry:Entry(Str, Str) instead of just Entry(Str, Str)).
So, we evaluate those parents all the way up to get each step.

## Parents and Environments Are Mutually Exclusive

(PEAME)

Remember from TMRE and NTKPRR:

-   FunctionTemplata(env: IEnvironment, function: FunctionEnvEntry)

-   FunctionEnvEntry(parent: Option\[IEnvEntry\], function: FunctionA)

-   (same with structs and interfaces)

We only need to know the parent\'s rules if we don\'t already have all
of the runes that were produced by those rules. For example, if we
already know that Key is Str and Value is Str, then we don\'t need to
know the Key = Ref(\_, Kind(imm)) rule.

If we included those rules anyway, when the runes were already in the
environment, that would probably confuse things.

When we support nested structs better, we\'ll need assertions and tests
that make sure that in a FunctionTemplata (struct and interface too),
the parent environments and parents dont overlap.

## Must Scan For Declared Runes First

(MSFDRF)

Before we scan all the rules for a function, we need to look ahead and
see what all of our runes are. For example:

> fn add\<K\>(list &List\<T\>) V
>
> where V Ref = Something\<T\>
>
> { \... }

Before we parse that V we need to scan ahead to see that it is in fact a
rune.


## Whether To Have Parent Function Environments (WTHPFE)

Function environments have three things:

-   parent environment

-   declared locals, a list of the locals that have been declared up
    > until now.

-   unstackified locals, a list of locals that have been unstackified.

Consider this snippet:

> fn main() {
>
> // 1
>
> let a = MyShip(3);
>
> // 2
>
> {
>
> // 3
>
> let b = MyShip(4);
>
> // 4
>
> drop(a);
>
> // 5
>
> }
>
> }

**Approach A: No Parent Function Environments**

1.  FunctionEnvironment(package, \[\], \[\])

2.  FunctionEnvironment(package, \[a\], \[\])

3.  FunctionEnvironment(package, \[a\], \[\])

4.  FunctionEnvironment(package, \[a, b\], \[\])

5.  FunctionEnvironment(package, \[a, b\], \[a\])

As you can see, environments in a block remember the locals from their
parent environment.

**Approach B: With Parent Function Environments**

1.  FunctionEnvironment(package, \[\], \[\])

2.  FunctionEnvironment(package, \[a\], \[\])

3.  FunctionEnvironment(FunctionEnvironment#2, \[\], \[\])

4.  FunctionEnvironment(FunctionEnvironment#2, \[b\], \[\])

5.  FunctionEnvironment(FunctionEnvironment#2, \[b\], \[a\])

As you can see, environments in a block don\'t remember the locals from
their parent environment, but they do have a reference to the parent
function environment which does.

**Comparison**

I don\'t really see an advantage to either approach. We will arbitrarily
choose approach A for now.

**Note from later:** That arbitrary choice caused a minor inconvenience.
FunctionEnvironmentBox.getEffectsSince is used to figure out if inner
scopes

has to take in an earlier FunctionEnvironment to compare them, to see
any changes. It would have been easier to

**Another note from later:**

-   We want block environments, so that we can look at the state of the
    > world as of the nearest containing loop, so that breaks can
    > destroy any variables.

-   When we make a closure, the \_\_call function is actually part of
    > the struct\'s environment, so we\'ll have a:

    -   \"child\" block environment, whose parent is the\...

    -   function environment, whose parent is the\...

    -   struct environment, whose parent is the\...

    -   surrounding block environment

-   If we want the child block environment to contain the surrounding
    > block\'s locals, we\'ll need to pass them down to where the
    > FunctionTemplar evaluates the \_\_call function, via arguments.

It seems like it would be easier to switch to B.

-   NodeEnvironment would look at anything defined in self, then ask
    > parent.

-   FunctionEnvironment would ask its parent.

-   StructEnvironment would, if its a lambda struct, look at its
    > members.

But we haven\'t yet.

## Lambdas Have Readwrite Self Parameters

(LHRSP)

Right now, lambdas\' \_\_call take &!self, not &self.

In the future, we could automatically infer whether it can by a &self,
by looking at how it\'s used inside the body. If theres never any
mutates, or handing out of &!, then we can make it a &self.

Just making all lambdas take &!self is a stepping stone.

Also, later we\'ll remove permissions anyway.

## Adding Constructors To Environments (ACTE)

Some functions (drop, \_\_call) are put into the struct\'s environment
itself. That way, when we use it as an argument, OverloadTemplar will
look into the args environments, see those functions, and consider them
as candidates.

This doesn\'t work for constructors though, because we don\'t hand the
struct\'s type in as an argument to its constructor.

So instead, it lives in the same environment the struct itself lives in.
It\'s a sibling, so to speak.

This is also good because it lets the function live in the overload
index.

We can also add the constructor function under a parent interface\'s
name, to facilitate sealed interface constructors.

## Difference between TemplatasStore and Environment (DBTSAE)

TemplatasStore is the templatas that are *defined* in a certain
namespace.

An environment is all the things that are *visible* from within there.

## Last Names for Anonymous Substructs and Constructors (LNASC)

you know whats weird?

-   if we have an interface named IBork, say at my.test.IBork

-   it will have an anonymous struct for it.

-   that anonymous struct will have a constructor, named IBork, because
    > we need to be able to call it like IBork.

that last thing will need to be in the my.test namespace, like fn
my.test.IBork, so that it can be called like IBork({ \_ + 3 }).

there are two ways to do this:

-   when a function like my.test.IBork.AnonStruct.IBork is added to an
    > environment, also copy it to its parent envs, as fn
    > my.test.IBork.IBork and fn my.test.IBork.

-   just represent its name as my.test.(constructor for (anon substruct
    > of (IBork)), so only three parts, and the last one has some inner
    > stuff.

for now we\'ll go with the latter.

## Block Environments Are For Breaks (BEAFB)

We used to have a FunctionEnvironment travel through the function, and
we would add new locals, add \"unstackifies\", and generally have a good
time.

However, for the break statement, we need to be able to see what
variables were introduced since the latest loop. The easiest way to do
that was to have a child environment which remembered the kind of node
it was for. Hence, NodeEnvironment.

We don\'t make a new NodeEnvironment for every node, just a few things.
We could change that and make a new one for every node if we wanted to,
someday.

# Notes

a Namespace is a bunch of templatas.

Global has a map of namespaces.

an Environment has a list of namespaces. plus a current mutable one
perhaps. plus a vector of ones imported in.

perhaps we can implicitly import the parent namespaces\...

TemplatasStore is basically namespace right now, we should rename it
later.

in each function/struct/interface/impl/whatever, we should remember the
vector of imports that are currently open. then, when we pull them from
the namespace and make them templatas, thats when we assemble a little
custom environment for that templata. this is really only useful for
closures though, i think\... maybe itd be nice if we could blend the
two; have a lazily made environment (on stamp) for most things, but an
eagerly made one for closures.

two options:

Option A: make all namespaces the same. locals and moveds would be part
of the environment, as special templatas. basically, we\'re making an
environment into an entity, and templatas are components.

Option B: have namespaces be traits, like they are now.

honestly, im kind of a fan of the traits, because it means we can have
e.g. FunctionEnvironment, which knows exactly where the locals and
moveds are, and it can modify them directly. theyre also a bit more self
describing, you can see what templatas are in them.

FunctionEnvironment has easy access to its return type.

We could have a BlockEnvironment that has access to its declared locals.

and they can have nice methods on them.

and, it makes it easier to modify them to produce something new.

it also means that we can know more specifically what our parent is.

for example, a PackageEnvironment knows it has no parent

we wanted this templatasstore stuff so we could do parallel lookups. but
perhaps we still can?

some options for knowing an environment\'s parent:

-   have a List\[IEnvironment\] in the env. Every env will have a list
    > of all its ancestors. Seemingly O(n\^2) but actually O(n), as
    > these linked lists share their nodes.

-   carry a List\[IEnvironment\] next to the env

    -   seems strictly worse than having it in the env.

-   every env has an Option\[IEnvironment\] parent, or in the case of
    > PackageEnvironment, it doesnt.

should an env have a list\[TemplatasStore\] of things above it? or a
pointer to its parent env?

reasons no:

-   it forces everything to be a templata, if it wants to be seen by
    > anyone.

-   it makes it impossible to reconstruct an env when we do a TMRE. it
    > would force us into the component-based approach, basically.

a package environment will need to have at least these things from
imported namespaces:

-   either

    -   all struct names and templata types

    -   all StructAs

-   either:

    -   all interface names and templata types

    -   all InterfaceAs

-   either:

    -   function overload index

    -   all FunctionAs and defining environments

for now we\'re going with the latter on each, but we should probably do
the former on each soon.

right now we have siblings which are calculated before hand, kind of
astrouts macros.

then we have child entries which are astrouts added to a stamped
thing\'s environment.

when we make a closure, there is no structA though. right now we kinda
wing it.

lets wing it elsewhere too i guess =\\

we\'ll have to resolve this when we split templar.
