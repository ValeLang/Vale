# Perfect Virtuals

[[Perfect Virtuals]{.underline}](#perfect-virtuals)

> [[Motivation]{.underline}](#motivation)
>
> [[Background]{.underline}](#background)
>
> [[Rust]{.underline}](#rust)
>
> [[C++]{.underline}](#c)
>
> [[JVM]{.underline}](#jvm)
>
> [[Implementation]{.underline}](#implementation)
>
> [[Hybrid Approach: Fat Pointers and Perfect
> Virtuals]{.underline}](#hybrid-approach-fat-pointers-and-perfect-virtuals)
>
> [[Comparison of Languages]{.underline}](#comparison-of-languages)
>
> [[Comparison with Rust]{.underline}](#comparison-with-rust)
>
> [[Hybrid Approach]{.underline}](#hybrid-approach)
>
> [[Perfect Virtuals without Fat
> Pointers]{.underline}](#perfect-virtuals-without-fat-pointers)
>
> [[Rust Inheritance and
> Thunks]{.underline}](#rust-inheritance-and-thunks)

Note: This is still a rough draft! I'm not 100% sure about any of the
claims involving other languages. Please comment if you find
inaccuracies or are skeptical of anything!

## Motivation

This doc is to describe an approach for calling interfaces' methods.
Indeed, there are already a lot of well-established methods for calling
an interface's method:

-   Rust uses "trait objects", also known as "fat pointers". For
    > example, if you have an ICar reference which happens to point at a
    > Civic struct, that ICar reference is actually two pointers: a
    > pointer to the Civic, and a pointer to Civic's ITable (a global
    > const array of function pointers, to Civic's implementations for
    > all of ICar's methods). Calling a method is as simple as following
    > that second pointer to the ITable, getting the function pointer,
    > and calling it.

-   C++ has a vptr at the beginning of every object, which points at a
    > global const VTable, which, like an ITable, is an array of
    > function pointers. For example, a Civic will always have a vptr
    > pointing at the Civic VTable. If Civic implements ICar, then every
    > Civic object will contain an ICar object. The ICar object also has
    > a vptr, pointing to a different VTable. So, combined, the Civic
    > actually indirectly contains two vptrs. In C++, if an object
    > implements N interfaces, it will have N additional vptrs.

-   In Java, every object points at some "class info" data. This data
    > contains a list of all implemented interfaces, and also all the
    > methods it implements. The JVM will search through these for the
    > method it needs, and then call it.

These approaches each have their benefits and drawbacks:

-   Rust is amazingly fast; we only have to get the method pointer out
    > of the ITable, which we already conveniently have a reference to.
    > But:

    -   It requires fat pointers, which means every pointer is 16 bytes
        > instead of 8.

    -   It doesn't do inheritance hierarchies; the fat pointer / ITable
        > approach is insufficient to allow this (more on this later).

-   C++ is somewhat fast, and the object layouts arguably make a lot of
    > sense, but:

    -   It requires special methods called thunks to properly call an
        > interface method. When we call an interface method, it goes
        > through one of these thunks, the thunk then calls the desired
        > method.

-   Java is compact; pointers are thin, and objects don't have multiple
    > vptrs. But:

    -   Calling a method requires an O(n) search to determine the exact
        > function pointer to call. Note, this is addressed by the
        > Jalapeño JVM.

-   [[Jalapeño
    > JVM]{.underline}](https://www.research.ibm.com/people/d/dgrove/papers/oopsla01.pdf)
    > doesn't do a linear search; it looks in a hash table for the
    > method to call, but requires thunks in the case of collisions.

This doc describes an alternative method called "Perfect Virtuals,"
which has different tradeoffs. In the Perfect Virtuals approach, we use
perfect hashing to look in an "ETable" to find an object's ITable. This
has some benefits:

-   We call interface methods In constant-time (unlike Java's O(n)
    > method search),

-   It requires no fat pointers (like in Rust)

-   it requires no thunk functions (like in [[Jalapeño
    > JVM]{.underline}](https://www.research.ibm.com/people/d/dgrove/papers/oopsla01.pdf)
    > and C++)

-   It requires no extra space in each object for all implemented
    > interfaces (Like C++'s vptrs)

-   Can upcast and downcast for free (Like JVM, and unlike C++, and Rust
    > can't upcast)

Despite the name, there are drawbacks:

-   It takes 11, 14, 22, or 25 (depending on the flavor of ETables)
    > instructions to get the function pointer. Compare this drawback
    > to:

    -   Rust, which takes only 2 instructions (add + load) to get the
        > function pointer from its fat pointer,

    -   C++, which takes 3+N instructions (load + add + load), N = a
        > call through a thunk method.

    -   Jalapeño JVM, which takes 4+N (load + add + load + modulus?), N
        > = a possible call through a thunk method.

-   The ETables take up space.

This doc will first describe a simple implementation of Perfect
Virtuals, and then further down will discuss optimizations and hybrid
approaches which try to mitigate these drawbacks.

## Background

Let's say we have this interface and these classes:

> interface IShuttle {
>
> void dock();
>
> }
>
> class Spaceship {
>
> int a;
>
> virtual void fly() { ... }
>
> }
>
> class Marauder extends Spaceship implements IShuffle {
>
> int x;
>
> override void fly() { ... }
>
> virtual void maraud() { ... }
>
> virtual void fireLasers() { ... }
>
> override void dock() { ... }
>
> }
>
> class PuddleJumper extends Spaceship implements IShuffle {
>
> int n;
>
> // PuddleJumper reuses Spaceship.fly
>
> virtual void dialStargate() { ... }
>
> override void dock() { ... }
>
> }

These might be the C++ vtables for Spaceship, Marauder, and
PuddleJumper:

**Spaceship:**

  --------------------------------------------------------------------------
  **Index**   **Signature**                **Implementation**
  ----------- ---------------------------- ---------------------------------
  0           void fly()                   Spaceship.fly

  --------------------------------------------------------------------------

**Marauder:**

  --------------------------------------------------------------------------
  **Index**   **Signature**                **Implementation**
  ----------- ---------------------------- ---------------------------------
  0           void fly()                   Marauder.fly

  1           void maraud()                Marauder.maraud

  2           void fireLasers()            Marauder.fireLasers

  3           void dock()                  Marauder.dock
  --------------------------------------------------------------------------

**PuddleJumper:**

  --------------------------------------------------------------------------
  **Index**   **Signature**                **Implementation**
  ----------- ---------------------------- ---------------------------------
  0           void fly()                   Spaceship.fly

  1           void dialStargate()          PuddleJumper.dialStargate

  2           void dock()                  Marauder.dock
  --------------------------------------------------------------------------

Calling Spaceship's fly(), or Marauder's maraud() or fireLasers(), or
PuddleJumper's dialStargate() functions are beautifully simple; those
are at predetermined indices at all times:

-   Spaceship and all of its subclasses have fly() at index 0.

-   Marauder and all of its subclasses have maraud() at index 1, and
    > fireLasers() at index 2.

-   PuddleJumper and all of its subclasses have dialStargate() at index
    > 2.

However, calling dock() on any of IShuttle's subclasses is difficult.
There are three main approaches:

### Rust

In Rust, we have "ITables". For every struct, we have an ITable for
every interface it implements. In our example, we'd have two ITables:

**IShuttle for Marauder:**

  --------------------------------------------------------------------------
  **Index**   **Signature**                **Implementation**
  ----------- ---------------------------- ---------------------------------
  0           void dock()                  Marauder.dock

  --------------------------------------------------------------------------

**IShuttle for PuddleJumper:**

  --------------------------------------------------------------------------
  **Index**   **Signature**                **Implementation**
  ----------- ---------------------------- ---------------------------------
  0           void dock()                  PuddleJumper.dock

  --------------------------------------------------------------------------

Whenever we have a "trait object", in other words a reference to an
IShuttle, that reference is actually made of two pointers: the pointer
to the data, and a pointer to which ITable.

### C++

C++ also has references to the ITables, though they live inside each
object.

Whereas we'd expect something like this underlying struct for
PuddleJumper:

> struct PuddleJumper {
>
> ClassInfo\* \_\_vptr;
>
> int a;
>
> int n;
>
> }

we actually have this:

> struct PuddleJumper {
>
> Spaceship \_\_spaceship;
>
> IShuttle \_\_shuttle;
>
> int n;
>
> }
>
> struct Spaceship {
>
> ClassInfo\* \_\_vptr;
>
> int a;
>
> }
>
> struct IShuttle {
>
> ClassInfo\* \_\_vptr;
>
> }

in other words, we indirectly have \*two\* vptrs inside PuddleJumper.

When we say:

> IShuttle\* myShuttle = new PuddleJumper();
>
> myShuttle-\>dock();

there's actually a hidden add instruction; myShuttle points to the
IShuttle inside our PuddleJumper.

That second \_\_vptr actually points to separate vtables, which look
like to Rust's ITables:

**IShuttle for Marauder:**

  ----------------------------------------------------------------------------
  **Index**   **Signature**         **Implementation**
  ----------- --------------------- ------------------------------------------
  0           void dock()           virtualthunk_Marauder_IShuttle::dock

  ----------------------------------------------------------------------------

**IShuttle for PuddleJumper:**

  ----------------------------------------------------------------------------
  **Index**   **Signature**         **Implementation**
  ----------- --------------------- ------------------------------------------
  0           void dock()           virtualthunk_PuddleJumper_IShuttle::dock

  ----------------------------------------------------------------------------

(Note: I made up the names, I don't know how they're named in C++
compilers)

"What are these virtual thunks?"

In the myShuttle-\>dock() above, remember that myShuttle is \*not\* a
pointer to a PuddleJumper, it's a pointer to an IShuttle, which is 4
bytes past what PuddleJumper::dock() expects as its hidden 'this'
argument. So, the virtual thunk method corrects that and calls the
desired dock():

> void virtualthunk_PuddleJumper_IShuttle::dock(IShuttle\* shuttle) {
>
> PuddleJumper::dock(shuttle - 4);
>
> }

### JVM

In Java, the invokeinterface instruction has to manually [[search
through the
vtable]{.underline}](https://stackoverflow.com/questions/1504633/what-is-the-point-of-invokeinterface)
for the signature we wish to call. For example:

> IShuttle myShuttle = new PuddleJumper();
>
> myShuttle.dock();

Java would get a pointer to the PuddleJumper vtable, but not know it was
a PuddleJumper. It would then search through until it gets to index 2,
which matches the signature it wants to call.

However, the [[Jalapeño
JVM]{.underline}](https://www.research.ibm.com/people/d/dgrove/papers/oopsla01.pdf)
has a method of doing this in amortized constant time; it puts the
signatures into a hash table rather than an array, where they key is the
method's integer "selector". Selectors are not unique, and there might
be collisions, in which case, that position in the hash table doesn't
point to the desired method, but instead points to a "routing" method
which figures out and ultimately calls the desired method.

## Implementation

Like Rust and C++, we have ITables. Every struct will have one ITable
for every interface it implements.

Like C++ and Java, every object will have a virtual pointer. This
virtual pointer will point to the middle of a "class info struct".

The class info struct contains:

-   the VTable

-   metadata for the ETable,

-   the ETable itself.

An ETable is basicaly a hashmap\<interfaceID, ITable\*\>. When we want
to call an interface method on an object, we:

1.  Get the class info struct from the object,

2.  Get the ETable from the class info struct,

3.  Get the ITable from the ETable,

4.  Get the method pointer from the ITable.

More details in the next section.

The Super Tetris Table is the best implementation, but to explain it, we
have to build up to it by explaining how we would use a Hash Table, a
Simple ETable, a Leveled ETable, and then a Tetris ETable.

explain stuff here

## Hybrid Approach: Fat Pointers and Perfect Virtuals

Perfect Virtuals can actually support fat pointers, to obtain all of the
benefits of zero-cost abstractions.

The only requirement for zero-cost abstractions is that we have ITables;
every struct has to have an ITable for every interface it implements.
So, a language can have fat pointers which point at the same ITables
that Perfect Virtuals' ETables point at.

Constant-time interface calling is a primary benefit of Perfect
Virtuals, but with that taken away, what's the point of Perfect
Virtuals? If we look at the other benefits, we can see why a system
would want to implement both:

-   By supporting thin pointers, we don't need to know the receiver type
    > when we pass a pointer into a function. For example, if we had a
    > void(ICar\*)\* function pointer, we could assign our
    > void(IHonda\*)\* function into it. This is important for VTables;
    > without this capability, we'd have to use thunks.

-   Similarly, we could assign a IHonda\*()\* function pointer into a
    > variable that expects a ICar\*()\* function pointer. This is an
    > example of the Liskov Substitution Principle in action: an
    > IHonda\* can be supplied anywhere an ICar\* can be. Without this,
    > we'd have to use thunks for covariant return types.

-   The ETable allows for constant-time querying whether a certain
    > object implements a certain interface. If we want to know whether
    > our ICar\* points to an object that also implements IBoat, we can
    > do that in constant time.

-   The ETable allows us to downcast a superinterface to a subinterface,
    > or an interface to a superclass, for free. This is impossible with
    > just fat pointers and ITables; one would not be able to downcast
    > an ICar\* to an IHonda\*.

The last two points are needed to support pattern matching against
interfaces.

In this approach, one can easily convert between fat pointers and thin
pointers, in constant time.

One promising idea is to use fat pointers anywhere on the stack, and
thin pointers inside data structures.

## Comparison of Languages

(C++ and JVM comparisons coming soon)

### Comparison with Rust

#### Hybrid Approach

Recall the benefits of the hybrid approach, namely:

-   Supporting thin pointers to interface objects means we obey the
    > Liskov Substitution Principle, which is very handy for function
    > pointers.

-   We can check whether a given interface reference points to something
    > that also implements a given interface.

-   We can downcast from a super-interface to a sub-interface, or an
    > interface to a superclass that implements it. For example, if
    > JacksCivic extends Civic implements IHonda implements ICar, we
    > could downcast to IHonda or Civic.

Note, the last two points are moot for Rust, because Rust doesn't
support interfaces extending interfaces, or structs extending structs.
But, if Rust did support that kind of inheritance, it would be awkward
because it wouldn't be able to downcast or do these checks.

Stated differently, Perfect Virtuals allow us to go from this limited
world, to a world where we **can** have interfaces extending interfaces
and structs extending structs, with complete downcasting, checking, and
pattern matching.

Of course, the big drawback of Perfect Virtuals remains: the size of the
ETables. One has to consider whether that size increase is worth the
above benefits.

Note: If one is trying to make a Rust-like language support inheritance
and pattern matching, Perfect Virtuals aren't the only option. Instead,
we could have the object's vptr point to a class info which contains an
array of pair\<InterfaceID, ITable\*\>, and search through it in a
linear fashion. It is O(n), but it is simpler.

#### Perfect Virtuals without Fat Pointers

This section compares the non-hybrid approach, the one that has thin
pointers only, with Rust. The comparison is interesting, but more
difficult, as it's hardly apples-to-apples.

Both approaches accomplish constant-time interface method calls, but
they sacrifice different things.

Perfect Virtuals sacrifices some more instructions (11-25 compared to 2)
to call its interfaces, and it also sacrifices some space for its
ETables.

Rust sacrifices other things though. The most obvious ones are:

-   It uses fat pointers, which are 16 bytes instead of 8.

-   It can't downcast trait objects to other traits.

-   One cannot query at run-time whether a certain trait object
    > implements another trait object

-   It can't upcast trait objects (though theoretically, it could with
    > just an add+load, I think...)

The more complicated ones follow.

##### Rust Inheritance and Thunks

If one believes that people should always avoid inheritance, then this
point is moot. For people with this belief, Rust's difficulties in this
area would actually be a feature, a deterrent to using inheritance, and
an encouragement to use composition / entity component systems / and
class clusters.

However, for the rest of us, inheritance is sometimes the best design
option. In these cases, Rust actually has hidden costs. For example, if
we want struct Sub to inherit code and data from struct Base, Rust has
two good approaches:

-   composition+forwarding,

-   default-implemented traits,

and both of these require thunks.

In composition+forwarding:

-   struct Sub will contain a Base.

-   struct Base will have a companion trait called IBase.

-   struct Sub will implement IBase, and have abstract methods that
    > simply call the same method on the contained Base.

If you think about it, these functions are thunks.

In default-implemented traits:

-   struct Base will have a companion trait called IBase.

-   IBase's methods aren't abstract, they contain implementation.

-   IBase will also have methods to access member data. These methods
    > are called by the aforementioned implementation methods.

-   struct Sub will implement IBase, and wire up the member data access
    > methods.

If you think about it, those member data access methods are also thunks.

So, in both approaches, Rust requires the user to hand-roll thunks. With
this in mind, calling an interface method takes more than 2
instructions, and is more similar to C++.

Note: composition / entity component systems / class clusters also
involve thunks of sorts, but Perfect Virtuals would have those same
thunks.
