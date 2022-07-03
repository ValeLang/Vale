now lets say fn doCivicDance(this: virtual &Battlecruiser) **int** { ...
} (note how we added an int)

we would have to make a thunk and an impl:

doCivicDanceThunk(this: &Battlecruiser) void {

let unused = doCivicDanceImpl(this);

}

doCivicDanceImpl(this: &Battlecruiser) int { ... }

and only the thunk would ever appear in the vtables above it.

a thunk would be needed for any sort of convertible type. packs can be
truncated, things can be upcast... this would be a pretty cool feature.

### Requirements

If there's ever a virtual function, we need to be on the lookout for
subclasses who might have their own versions of this virtual function.

interface IBase { }

interface ISub implements IBase { }

struct MyStruct implements ISub { }

fn doThing(x: IBase) { }

fn doThing(x: ISub) { }

When we encounter doThing(:IBase), we just interpret it as a regular
function. But when we encounter doThing(:ISub) as well, alarm bells go
off. Someone who calls this might expect that doThing(:IBase) would be
called, but actually doThing(x:ISub) is called. This is why we **need**
a keyword.

interface IBase { }

interface ISub implements IBase { }

struct MyStruct implements ISub { }

fn doThing(**virtual** x: IBase) { }

fn doThing(x: ISub) { }

There's still a problem here, the person who wrote doThing(x: ISub)
might not realize they're hijacking someone else's function. Or, they
might not realize that they typed the name wrong. So, we need to put the
override keyword in.

interface IBase { }

interface ISub implements IBase { }

struct MyStruct implements ISub { }

fn doThing(**virtual** x: IBase) { }

fn doThing(**override** x: ISub) { }

We can also do **abstract** instead of that **virtual**.

interface IBase { }

interface ISub implements IBase { }

struct MyStruct implements ISub { }

fn doThing(**abstract** x: IBase) { }

fn doThing(**override** x: ISub) { }

That'll signal the compiler to check that every single subclass of IBase
needs to implement doThing.

#### Templates

interface IBase:T { }

interface ISub:T implements IBase:T { }

struct HighStruct:T implements IBase:T { }

struct LowStruct:T implements ISub:T { }

fn doThing:T(**virtual** x: IBase:T) { }

fn doThing:T(**override** x: ISub:T) { }

fn doThing:T(**override** x: LowStruct:T) { }

This is a scary situation.

fn main(a: IBase:Int) { }

-   doThing is never stamped anywhere, so pretend it doesn't exist.

-   ISub, HighStruct, LowStruct are never stamped anywhere, so ignore
    > those.

-   Stamped: IBase:Int

fn main(a: ISub:Int) { }

-   doThing is never stamped anywhere, so pretend it doesn't exist.

-   ISub is stamped, and it implements IBase, so IBase:Int needs to be
    > stamped.

-   Stamped: IBase:Int, ISub:Int

fn main(a: HighStruct:Int) { }

-   doThing is never stamped anywhere, so pretend it doesn't exist.

-   HighStruct:Int is stamped, and it implements IBase, so IBase:Int
    > needs to be stamped.

-   Stamped: HighStruct:Int, IBase:Int

fn main(a: LowStruct:Int) { }

-   doThing is never stamped anywhere, so pretend it doesn't exist.

-   LowStruct:Int is stamped, and it implements both interfaces, so they
    > need to stamp.

-   Stamped: LowStruct:Int, IBase:Int, ISub:Int

fn main(a: IBase:Int) {

doThing(a)

}

-   doThing is called with IBase:Int, so doThing(:IBase:Int) needs to be
    > stamped.

-   doThing:Int is stamped, so there's a fn doThing:Int(virtual x:
    > IBase:Int)

-   There's never any subclasses of IBase:Int, so do nothing.

-   Stamped: IBase:Int, doThing(:IBase:Int)

fn main(a: ISub:Int) {

doThing(a)

}

-   IBase:Int needs to be stamped.

-   doThing(:ISub:Int) needs to be stamped.

-   However, we're guaranteed in this program to never need to call
    > doThing(:IBase:Int).

-   What does this mean!? Doesn't this mean that we never stamp the
    > thing it overrides?

-   I'm fine with this. Lets not stamp the IBase:Int one.

-   But we still see that override keyword... and we need to check that
    > it overrides something... so I guess we do need to stamp the
    > IBase:Int one. Maybe later we can do an optimization to take out
    > the IBase:Int one if we know it's never called.

-   Also, it's possible that we got handed in a LowStruct. Wait, no it's
    > not, because LowStruct was never stamped. So, don't stamp it.

-   Stamped: ISub:Int, IBase:Int, doThing(:ISub:Int),
    > doThing(:IBase:Int)

fn main(a: LowStruct:Int) {

doThing(a);

}

-   LowStruct:Int was stamped, which means ISub:Int and IBase:Int need
    > stamping too.

-   doThing(:LowStruct:Int) is stamped, and we have to stamp all of its
    > ancestor functions to check the override keyword.

-   You know, by "ancestor" we really just mean "matching"\...

-   Stamped: LowStruct:Int, ISub:Int, IBase:Int,
    > doThing(:LowStruct:Int), doThing(:ISub:Int), doThing(:IBase:Int).

fn main(a: HighStruct:Int) {

doThing(a);

}

-   HighStruct:Int was stamped, which means IBase:Int is stamped too.

-   doThing(:HighStruct:Int) doesn't exist, but we did manage to stamp a
    > doThing(:IBase:Int).

-   Oddly enough, doThing(:LowStruct:Int) isn't initialized. Probably
    > because it's never been stamped itself.

-   Stamped: HighStruct:Int, IBase:Int, doThing(:IBase:Int).

fn main(a: IBase:Int) {

doThing(a);

let x: ISub:Int = doStuff();

}

-   From before, stamped: IBase:Int, doThing(:IBase:Int)

-   Now that :ISub:Int exists, we need to stamp ISub:Int and
    > doThing(:ISub:Int) and all of its ancestors.

-   More specifically, we need to look at all of our superclasses, and
    > for each:

    -   Look at all virtual functions that take in them (whether they
        > were stamped or not):

        -   Stamp everything and check we have one that overrides it.

-   Stamped: IBase:Int, doThing(:IBase:Int), ISub:Int,
    > doThing(:ISub:Int)

fn main(a: IBase:Int) {

doThing(a);

let x: LowStruct:Int = doStuff();

}

-   From before, stamped: IBase:Int, doThing(:IBase:Int)

-   Now that :LowStruct:Int exists, we need to stamp LowStruct:Int and
    > ISub:Int. And since a doThing takes in a :IBase:Int, we have to
    > make overrides for all of our ancestors.

-   Stamped: IBase:Int, doThing(:IBase:Int), ISub:Int,
    > doThing(:ISub:Int), LowStruct:Int, doThing(:LowStruct:Int).

fn main(a: ISub:Int) {

doThing(a);

let x: LowStruct:Int = doStuff();

}

-   From before, stamped: ISub:Int, IBase:Int, doThing(:ISub:Int),
    > doThing(:IBase:Int)

-   We just made a LowStruct:Int, which means theoretically it could
    > make its way into that doThing(a)\... which means we have to stamp
    > doThing(:LowStruct:Int).

-   Stamped: ISub:Int, IBase:Int, LowStruct:Int, doThing(:ISub:Int),
    > doThing(:IBase:Int), doThing(:LowStruct:Int)

fn main(a: ISub:Int) {

doThing(a);

let x: HighStruct:Int = doStuff();

}

-   From before, stamped: ISub:Int, IBase:Int, doThing(:ISub:Int),
    > doThing(:IBase:Int)

-   We just made a HighStruct:Int, which means it actually can't ever
    > call doThing(a) because that takes a ISub:Int. Huh. BUT,
    > doThing(:IBase:Int) was stamped! According to our rules, we look
    > at all virtual functions that take in any of our superclasses, and
    > make sure we have overrides for each. So we stamp every doThing
    > possible but we don't find a doThing(:HighStruct:Int). That's
    > fine, we gave it a shot. Just use the default since it wasnt
    > abstract.

-   Stamped: ISub:Int, IBase:Int, HighStruct:Int, doThing(:ISub:Int),
    > doThing(:IBase:Int).

fn main(a: LowStruct:Int) {

doThing(a);

let x: HighStruct:Int = doStuff();

}

-   From before, stamped: LowStruct:Int, ISub:Int, IBase:Int,
    > doThing(:LowStruct:Int), doThing(:ISub:Int), doThing(:IBase:Int).

-   We just made a HighStruct:Int so we make sure IBase:Int is stamped,
    > yes it is.

-   HighStruct:Int was stamped, and we check to see any virtual
    > functions that take in our ancestors and see doThing(:IBase:Int).
    > So we stamp things until we find a doThing(:HighStruct:Int).

-   Stamped: LowStruct:Int, ISub:Int, IBase:Int, HighStruct:Int,
    > doThing(:LowStruct:Int), doThing(:ISub:Int), doThing(:IBase:Int),
    > doThing(:HighStruct:Int).

fn main(a: ISub:Int) {

doThing(a);

}

struct BlarkStruct implements ISub:Int { }

-   From before, stamped: ISub:Int, IBase:Int, doThing(:ISub:Int),
    > doThing(:IBase:Int)

-   We just made a new subclass. We look at ISub:Int, and see that it's
    > got a virtual function that takes it in, doThing(:ISub:Int). So,
    > we stamp everything in existence, and see that doThing(:ISub:Int)
    > is the best match, so that's what we go with.

-   So, nothing new is stamped.

fn main(a: ISub:Int) {

doThing(a);

}

struct BlarkStruct implements ISub:Int { }

fn doThing(override x: BlarkStruct) { }

-   From before, stamped: ISub:Int, IBase:Int, doThing(:ISub:Int),
    > doThing(:IBase:Int)

-   Now, something's weird here. We previously declared that
    > doThing(:ISub:Int) was the best possible match for BlarkStruct,
    > but we jumped the gun. Apparently we need to wait before declaring
    > that. We can still stamp everything in existence that matches it,
    > but we can't declare the best one yet.

interface IBase:T { }

fn doThing:T(**abstract** x: IBase:T);

struct SklopStruct implements IBase:Int { }

fn doThing(override x: SklopStruct) { }

-   Nothing is stamped until we get to that struct line.

-   struct SklopStruct stamps IBase:Int. However, there were never any
    > functions before this that were stamped which took in an
    > IBase:Int.

-   We get to fn doThing(override x: SklopStruct). According to
    > algorithm, we stamp all stampable functions that match the virtual
    > param's class, which means we stamp doThing(:IBase:Int). Note that
    > it is abstract. But we're making an override right now, so it's
    > all good.

-   Stamped: IBase:Int, doThing(:IBase:Int), SklopStruct,
    > doThing(:SklopStruct)

interface IBase:T { }

fn doThing:T(**abstract** x: IBase:T);

fn main(a: IBase:Int) {

doThing(a);

}

struct SkortStruct implements IBase:Int { }

fn doThing(override x: SkortStruct) { }

-   From before: stamped: IBase:Int, doThing(:IBase:Int)

-   We get to struct SkortStruct, IBase:Int is already stamped. But, for
    > each superclass we check all virtual functions that take them, we
    > find doThing(:IBase:Int). We stamp everything with the new struct,
    > which means we just stamped doThing(:SkortStruct). Note that it is
    > abstract. We have to hope that eventually we'll give a good
    > override.

-   We get to doThing(:SkortStruct), and stamp all stampable functions
    > that match the virtual param's class. Nothing new is stamped.

-   Stamped: IBase:Int, doThing(:IBase:Int)

interface IBase:T { }

struct SpartStruct implements IBase:Int { }

fn doThing(**abstract** x: IBase:Int);

fn doThing(override x: SpartStruct) { }

-   When we get to struct SpartStruct, it's stamping IBase:Int.

-   When we get to doThing(:IBase:Int), that exists now... and we try to
    > stamp all stampable functions that match it, but none appear.

-   The virtual param's class has subclasses stamped, so we look for any
    > doThing that are compatible with SpartStruct. None appear. So ends
    > our journey on this line. We pray.

-   We get to doThing(:SpartStruct). We stamp all stampable functions
    > that match this. None appear. This virtual param class has no
    > subclasses, so do nothing there. We're done.

-   Stamped: IBase:Int

interface IBase:T { }

struct ShoopStruct implements IBase:Int { }

fn doThing:T(**abstract** x: IBase:T);

fn doThing(override x: ShoopStruct) { }

-   When we get to struct ShoopStruct, it's stamping IBase:Int.

-   We get to doThing(:ShoopStruct). We stamp all stampable functions
    > that match the virtual param's class, which means we stamp
    > doThing(:IBase:Int).

-   ShoopStruct has no subclasses, so we do nothing.

-   We're done!

-   Stamped: IBase:Int, doThing(:IBase:Int)

interface IBase { }

struct RortStruct implements IBase { }

fn doThing:T(abstract x: T);

fn doThing(override x: RortStruct)

-   We get to doThing(:RortStruct). We stamp all stampable functions
    > that match RortStruct. That means we stamp an abstract
    > doThing(:RortStruct). We also stamp doThing for all of our base
    > classes, which means we stamp an abstract doThing(:IBase).

-   Stamped: doThing(:IBase) at least. ~~Possibly even an abstract
    > doThing(:RortStruct).~~

~~interface IA { }~~

~~interface IB { }~~

~~struct ForkStruct implements IA, IB { }~~

~~fn doThing:T(virtual x: T) { }~~

~~fn main(x: ForkStruct) {~~

~~doThing(x);~~

~~}~~

-   ~~Before, doThing(x), IA, IB, ForkStruct all already exist.~~

-   ~~Stamped: doThing(:ForkStruct), doThing(:IA), doThing(:IB)~~

~~interface IA { }~~

~~interface IB { }~~

~~struct X implements IA, IB { }~~

~~struct Y implements IA, IB { }~~

~~fn doThing:T(abstract x: T); // note from later: fails because all
levels have abstract.~~

~~fn main(x: X, y: Y) {~~

~~doThing(x);~~

~~}~~

-   ~~Stamped: doThing(:X), doThing(:IA), doThing(:IB)~~

-   ~~but now a doThing(:IA) exists, but what if we call it with a Y?
    > Crap... we have to stamp for the entire freakin' hierarchy.~~

-   ~~If we try this, and we try to stamp the entire hierarchy of, say,
    > GameEntity, we're screwed. lets back up a step, only allow fn
    > doThing:T(virtual x: MyInterface:T) in other words, it has to
    > specify which class, in there.~~

interface IGoblin:T { }

interface IWarrior:T { }

struct GoblinWarrior:T implements IGoblin, IWarrior { }

fn shout:T(abstract x: IGoblin:T);

fn shout:T(override x: GoblinWarrior:T) { }

fn main(g: GoblinWarrior:Int) {

shout(g);

}

-   Before shout(g), stamped IGoblin:Int, IWarrior:Int,
    > GoblinWarrior:Int.

-   Stamp shout(:GoblinWarrior:Int), shout(abstract :IGoblin:Int)

maybe a covariant family's root should be computed on the fly... that
way we dont have to keep updating it... and the families could be
self-merging, self-splitting?

Calling a function stamps it. There might be other ways to stamp a
function (selection?) so we'll just say "a function is stamped".

Whenever a function appears or is stamped, we:

-   Stamp all stampable functions that match the virtual param's class.
    > Try stamping with all superclasses of the virtual param class.
    > Error if none?

-   ~~For any that are marked override, make sure there's a virtual one
    > that they override.~~ Does this check have to come later?

-   If the virtual param's class has no subclasses stamped, stamp
    > nothing extra.

-   If the virtual param's class has subclasses stamped, then stamp
    > versions for all of its subclasses.

-   Don't yet declare which functions will go into the vtables, because
    > we need to wait to see if any more functions appear that will be a
    > better fit (such as doThing(:BlarkStruct))

When a struct appears or is stamped, we:

-   Stamp all superclasses.

-   For each superclass (or perhaps we just need to check our parent
    > because recursion?):

    -   Look at all virtual functions that take in them (whether they
        > were stamped or not):

        -   Stamp everything with the new struct, but don't yet check
            > that we have one that overrides it. In the cases of
            > SklopStruct and SkortStruct, we're stamping abstracts but
            > will later come across an un-templated override, which is
            > fine.

At the end of the program:

-   Do the edge carpenter and covariant family things like normal. Check
    > no abstract functions are being entered into vtables. Should all
    > be good.

Guarantees:

-   If there's a virtual function that takes in a base type, we've
    > attempted stamps for every single subtype.

-   If there's a virtual function that takes in a sub type, we've
    > attempted stamps for every single supertype (because we attempted
    > to stamp every function that matches this)

-   If there's a struct, we've stamped all of its base types.

-   If there's a struct, and a virtual function somewhere takes in its
    > base type, we've attempted stamps for all of those functions with
    > this new struct.

You know, it's actually physically impossible to hand in a type that's
not yet stamped. So we will \*always\* be stamping a type first, and
\*then\* using it. We will never stamp a function that takes in a new
type. The new type already exists.

From an interface's perspective:

-   They stamped my base class

-   They found/stamped some functions that take it in as a virtual

-   I got stamped! Now I stamp me functions until I override all those
    > virtual funcs that take in my ancestors.

-   Oh look, a new virtual function is stamped which takes in my base
    > class. We do stamps for all the subclasses including me.

-   Oh look, a new virtual function is stamped which takes in my sub
    > class. We do stamps for all the superclasses including me.

for any virtual function that takes in a PlainSubInterface, for every
subclass of PlainSubInterface that's stamped, we need an override that
fits it. which means:

-   when we stamp a new templated virtual function that takes in a
    > PlainSubInterface, loop over all stamped subclasses of
    > PlainSubInterface, and try to stamp an override of this function
    > for it.

-   when we encounter a new PlainSubInterface subclass, loop over all
    > stamped virtual functions that take in a PlainSubInterface
    > subclass, and try to stamp an override of those things, for this
    > subclass.

an interface specialization is only ever instantiated if:

\- we refer to that type at any time.

what does it mean to instantiate an interface specialization anyway?

not much, but it is a factor in whether other things are instantiated.

a struct specialization is only ever instantiated if:

\- we refer to that type at any time

a method specialization for struct is only instantiated if:

\- theres a call with struct or struct\'s superclasses, and

\- struct or struct\'s subclasses are ever instantiated

a method specialization for an interface is instantiated if:

\- there\'s a call with interface or interface\'s subclasses, and

\- interface or interface\'s subclasses are ever instantiated

you know, the difference between superstruct and substruct is purely a
midas concern\...

it just means we can shortcut into the vtable and we know its data is at
the top of this struct

To "scout" for a function means look for a function that matches.

It will first look for all non-templated function that match.

It will then look for all stampable candidates, but **don't** stamp
them.

This might involve stamping any structures and interfaces in the
prototype, that's fine, keep those.

Scouting for a function might generate lots of candidates. For example:

> interface IBase:T { }
>
> interface ISub:T implements IBase:T { }
>
> struct MyStruct:T implements ISub:T { }
>
> fn doAThing:T(x: IBase:T) { }
>
> fn doAThing:T(x: ISub:T) { }
>
> fn doAThing:T(x: MyStruct:T) { }

If we ever call doAThing(:MyStruct:Int), then we'll be scouting for a
doAThing(:ISub:T). This scouting will return those first two prototypes,
doAThing(:IBase:Int) and doAThing(:ISub:Int).

Anyway, now we have non-templated and potentially-stampable functions.

If there are none, throw an error.

If there are multiple, figure out which is the best fit (give some
priority to the non-templated ones?).

If the best fit is abstract, throw an error.

Return the best fit.

Fun fact: scouting for a function is basically overload resolution.

Overall algorithm:

-   Gather a list of all functions, structs, templated and not, into the
    > env.

-   Process all non-templated structs and interfaces. Processing this
    > will be cheap because there's no covariant families yet.

-   Process all non-templated prototypes that are known the to the env,
    > and process them. This will indirectly stamp a lot of things (any
    > template-specified struct/interface parameters) and so the process
    > is firing on all cylinders at this point.

-   Go through all the function bodies, and whenever they stamp any
    > structs or functions, process them immediately.

Processing:

Whenever a function (prototype P) appears or is stamped, we:

-   If it has no covariant index (no override/virtual/abstract) skip.

-   Figure out the covariant index C, the one with the override keyword.

-   For each ancestor B:

    -   Scout for a function like P has an 'abstract' or 'virtual' B at
        > C.

    -   If none exist, stop, otherwise continue.

    -   Stamp it. (this might indirectly add more subclasses, careful)

    -   Make a family for it.

    -   If a subclass and a superclass ever both have abstract or
        > virtual, throw an error. (In the future, maybe we can say
        > 'abstract override' or something)

    -   For each **known/stamped** subclass S of B:

        -   Scout for a function with S in P at C.

        -   Check it has 'override', else throw an error.

        -   Stamp it. (this might indirectly add more subclasses,
            > careful)

        -   Put it in the family.

    -   Make sure any subclasses that were added during stamping of the
        > others were also added. A while loop could work nicely...

    -   If there are any gaps in the covariant family, throw an error.

Later on, there might be more structs that are added to the hierarchy
that we're serving, and at that point, they'll need to add themselves to
the covariant family.

> When a struct S appears or is stamped, we:

-   If we have a base class B:

    -   If it doesn't exist, stamp it. (This is recursive!)

    -   Find any covariant families (prototype P, covariant index C)
        > that serve B. For each:

        -   Scout for a function that puts S in P at C, put it into the
            > family.

We might have reentrancy problems. We should queue up the work first
(add the requirement to the blueprint) and fill it in later. If it's
already in the blueprint, we can skip. After each time we call out, we
need to re-check the hierarchies and covariant families to re-figure-out
what holes need to be filled.
