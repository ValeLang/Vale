There are two parts to a constructor:

-   assembling

-   initialization

In the assembling phase, we can only create a member and reference
previously created members.

When we get to the end of the function, or reference \'this\' for the
first time, assembling phase is over. All members must be created by the
time this happens.

Once we reference \'this\', we can use \'this\' and all of its members
freely.

> fn(constructs this: Knight) {
>
> // Before the next line, can\'t reference this.item
>
> let this.item = Item();
>
> // Now we can reference this.item.
>
> // Since we reference \'this\' on the next line, here is where
> \'this\' conceptually becomes a
>
> // thing, we\'ve entered initialization phase.
>
> println(&this);
>
> }

Two corresponding parts to a destructor:

-   deinitialization

-   deallocating

In deinitialization, we can use \'this\' as much as we want. Once we
move a member out (via a move to a function call, including into
destructor()), \'this\' is conceptually dead and we\'ve entered
deallocating phase. In deallocating, we can move members out and
reference members that have not yet been moved out. By the end of
deallocating phase, all owned members must have been moved out.

> fn(destructs this: Knight) {
>
> // We can reference \'this\' here because we haven\'t moved any
> members.
>
> println(&this);
>
> // Since we move a member on the next line, deinitialization is over,
> and \'this\' is no
>
> // more, we\'ve entered the deallocating phase.
>
> unlet this.item;
>
> }

If we make members in the same order as they\'re declared in, the
compiler can automatically put in all the destructor calls. If the user
doesn\'t, then they need to define their own destructor and manually
unlet all the members.

We need to support clearing out a borrow reference in the destructor. We
can\'t just pass a borrow ref to destructor() and have it ignored
because that looks very misleading:

> fn(override destructs this: Knight) {
>
> destructor(this.myBorrowRef);
>
> }

so we\'ll have an \"unlet\" instruction.

> fn(override destructs this: Knight) {
>
> unlet this.myBorrowRef;
>
> }

We can even pass it to other functions:

> fn(override destructs this: Knight) {
>
> doThings(unlet this.myBorrowRef);
>
> }

At this point, the user could call destructor() OR do an unlet on their
owned things. It\'s not terrible, but it\'s ambiguous. unlet is good
because it works on borrow refs too, but moving into a function is super
nice. Let\'s require unlet, and say that it returns the owning
reference.

> fn(override destructs this: Knight) {
>
> unlet this.item; // calls the destructor
>
> }
>
> fn(override destructs this: Knight) {
>
> destructor(unlet this.item); // equivalent, calls the destructor
>
> }
>
> fn(override destructs this: Knight) {
>
> consumeItem(unlet this.item); // moves it away
>
> }

but thats inconsistent with regular functions where we can just move
away without unlet.

we could require them to do the long form:

> fn(override destructs this: Knight) {
>
> let (myBorrowRef, \_, thing, otherthing) = this.splode();
>
> destructor(thing);
>
> unlet myBorrowRef;
>
> destructor(otherthing);
>
> }

but that means nothing in the struct can be inlined...

so, we probably just have to stick with the ambiguity.

When you leave the name of a constructor blank, it\'s automatically
called \_\_construct.

When you leave the name of a destructor blank, it\'s automatically
called \_\_destruct.

Whenever we define a destructor not named \_\_destruct, a wrapper
function called \_\_destruct is created which just calls yours.

So, you can always get at the destructor by looking for \_\_destruct.

Destructors can take parameters and return values! It just means that
the destructor call can\'t be inserted for you. But V will yell at you
if you didn\'t insert it yourself.

Destructors can have names, sometimes useful if you have multiple
destructors.

stl will have a \"destruct\" method which will just let the parameter go
out of scope.

myThings...(v.destruct)();

...(stuff here) will be interpreted as a namespaced lookup.

**The backref \"problem\"**

struct Knight {

item: Item;

}

fn Knight(constructing this: Knight) {

let this.item = Item(this);

}

struct Item {

holder: &Knight;

}

Remember how the default destructor will first make sure that \'this\'
has no references to it, and then will start destructing the members.
Because of this, item still has a reference to it, and we\'ll panic.

There\'s some unsafety lurking in here though. In Item\'s constructor,
we have a pointer to the knight before it\'s completely constructed.
Correspondingly, in Item\'s destructor, we have a pointer to the Knight
after it\'s started destructing.

This is actually prevented by the constructor rules. \"let this.item =
Item(this)\" is using \'this\' before all members are \'let\'d and V
will detect this and give a compiler error.

We instead have to have an Item like this:

struct Item {

holder: ?&Knight;

...

fn setHolder(this, holder: &Knight) { mut this.holder = holder; }

}

Now, Knight\'s constructor will have to look like this:

fn Knight(constructing this: Knight) {

let this.item = Item();

this.item.setHolder(&this);

}

This is valid. The first use of \'this\' will check that all members
have been constructed.

In this case, the destructor should do the exact opposite:

fn destructor(destructing this: Knight) {

this.item.setHolder(null);

// After here, the default destructor logic will kick in: destruct any
members

// in the reverse order they were declared in.

}

An alternate form, using the same workaround:

fn Knight(constructing this: Knight, item: Item) {

let this.item = item;

this.item.setHolder(&this);

}

Same as above, we should null out the holder before destruction.

Imposing this \"null it back out!\' requirement on things sounds bad,
but remember that the item already has a valid state where it had a null
holder; that\'s how it is right after creation. What we\'re enforcing
here is that they go backward.

### Implementation

**Templar**

Templar will figure out when a mutable value goes out of scope, and call
destructor() on it.

Conceptually, there\'s a templated destructor which can apply to every
mutable struct:

fn:(T: mut \^ ref) destructor(virtual this: T);

> fn:(T: mut \^ ref) destructor(override destructing this: T) {
>
> \_\_destructMembers(this);
>
> }
>
> fn:(T: mut \^ ref) \_\_destructMembers(override destructing this: T) {
>
> reverse(\...obj.splode()\...\_\_filter(\_.\_\_isOwning)\...)\...destructor();
>
> }

However, one can define one for themselves and have that called instead:

> fn destructor(obj: Marine) { ... }

If they define their own destructor, the call to \_\_destructMembers()
will be put at the end, **unless** they unlet any members, in which
case, they are required to unlet all the members, and the
\_\_destructMembers() call won\'t be put in.

Buuuut that\'s not how it really works, because it would make for
incredibly difficult to follow stack traces.

Instead, the Templar will manually insert the unlet statements.

We can\'t generate the destructors in a first pass of the program
because templated structs haven\'t been stamped yet.

We can generate it:

-   When we stamp it,

-   When we let it go out of scope (the virtualtemplar will hunt down
    > all possible subclasses and stamp them)

-   At program\'s end

We\'re going with the second one. We\'ll look for the \_\_destruct
method, which is guaranteed to exist, and call it with one parameter. If
we can\'t find a \_\_destruct method, we look for other \_\_destruct
methods which take in the same first parameter. If we can\'t find one,
we generate it then.

The destructor is required to:

-   For every member variable, call its destructor or move it away.

-   Call the Destroy2 instruction. The Destroy2 instruction is basically
    > translated to free(). It just deallocates. All members of the
    > struct shuld already be deallocated before the Destroy2
    > instruction.

So, recursively calling destructors will also recursively call Destroy2.

Keep in mind, if A contains B, C, D, that means A\'s destructor enters
before B C and D\'s.

**Hammer**

Immutables:

Hammer will track when immutable things are aliased and dealiased.
It\'ll introduce Alias3 and Dealias3 instructions.

Mutables:

When handling Destroy2, it will deallocate the struct and also Dealias3
all immutable members.

**Midas**

Dealias3 will:

-   For primitives, do nothing

-   For immutable structs, call the dealias function for it.

-   For immutable interfaces, call the -1th function in the vtable,
    > which points at the dealias function for the underlying struct.

The dealias function for a struct will dealias all the members.
