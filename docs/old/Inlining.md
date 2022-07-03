This page talks about the inl keyword, which inlines things, and the
implications it has for the rest of the language.

[[Inlining for Statically-Sized
Locals]{.underline}](#inlining-for-statically-sized-locals)

> [[Mutating]{.underline}](#mutating)
>
> [[Auto-inlining]{.underline}](#auto-inlining)

[[Inlining for Statically-Sized
Members]{.underline}](#inlining-for-statically-sized-members)

> [[Auto-inlining]{.underline}](#auto-inlining-1)

[[Inlining for Dynamically-Sized
Locals]{.underline}](#inlining-for-dynamically-sized-locals)

> [[Must be Final]{.underline}](#must-be-final)

[[Inlining for Dynamically-Sized
Members]{.underline}](#inlining-for-dynamically-sized-members)

> [[Dynamic Sizes]{.underline}](#dynamic-sizes)
>
> [[Inlines\' Constructors Must Return
> Size]{.underline}](#inlines-constructors-must-return-size)
>
> [[Must be Final]{.underline}](#must-be-final-1)
>
> [[Optimization]{.underline}](#optimization)

[[Unions]{.underline}](#unions)

Let\'s say we have this code:

> struct Warrior {
>
> hp: Int;
>
> strength: Int;
>
> damageRange: Pair:(Int, Int);
>
> weapon: IWeapon;
>
> }
>
> fn main() {
>
> let warrior = Warrior(10, 8, Sword());
>
> \...
>
> }

warrior is a pointer on the stack to a Warrior on the heap.

Inside Warrior, damageRange and weapon are pointers to allocations
somewhere *else* in the heap.

However, we might want *all* of this to be on the stack, in other words,
**inline**. The benefits:

-   The stack is pretty much always hot in the cache.

-   The stack doesn\'t suffer memory fragmentation.

This doc describes when and how we can use the inl keyword to put these
things on the stack.

## Inlining for Statically-Sized Locals

(statically-sized means we know its size at compile time, for example
for structs)

In the above example, we can put inl in front of warrior to make it
inline:

> let inl warrior = Warrior(10, 8, Sword());

However, this has a restriction: we can\'t move warrior while any borrow
references might possibly be active. If there are no borrow references,
we can move it (it will copy the bytes). This is a restriction similar
to Rust\'s rules.

If Warrior is immutable, then we can inline it without restriction. Note
that any references that would have shared it will now do a copy
instead.

### Mutating

There are three ways to mutate things:

**Swap**, like exch x, y;, swaps the two variables/members.

If both are non-inline, then everything\'s fine. Otherwise:

-   Check x has 0 incoming refs (and null out its weak pointers).

-   Check y has 0 incoming refs (and null out its weak pointers).

-   Swap the contents of their memory, bit by bit.

**Destroy-and-move-and-lend**, like mut obj.x = y, destroys the existing
value of x and moves y into it (and also lends it).

-   Check x has 0 incoming refs (and null out its weak pointers).

-   Check y has 0 incoming refs (and null out its weak pointers).

-   Move x into some temporary memory.

-   Copy y into x bit by bit.

-   Call the destructor on the temporary memory.

**Take-and-replace**, like exch obj.x = y, moves the new obj into the
field, while moving the old one out (and producing it as the result of
the expression).

-   Check x has 0 incoming refs (and null out its weak pointers).

-   Check y has 0 incoming refs (and null out its weak pointers).

-   Move x into the result memory.

-   Copy y into x bit by bit.

-   produce the tem

Someone who has borrow references will be surprised when their program
halts on these checks. **However, someone who has weak references will
have them nulled out and probably not know why.** We can\'t do this, we
need to make something explicit, give them a clue as to what they\'re
doing.

One idea: we already have a function that will explicitly do these
checks: uni. So, each of the above examples becomes:

-   exch uni(x), uni(y);

-   mut obj.x = uni(y)

-   exch uni(obj.x) = uni(y)

This could be implemented two ways:

-   exch uni(expr.field) = uni(expr) and exch expr.field = expr could be
    > entirely different, equal syntaxes.

-   In uni(expr.field), uni could be a function that returns an
    > addressible (the only one, in fact). I\'m not sure if that means
    > the field has to be a box though\...

We\'ll go with the first one. They\'ll be different syntaxes.

But\... isn\'t this super annoying? The user basically has to manually
unlink any upward references inside the inlined thing before moving them
out.

### Auto-inlining

Moving things that are on the heap is easy, it\'s just a conceptual
change. But moving something that\'s inline is trickier, because we\'re
destroying the old memory and copying it over to new memory.

A local can be inlined if it doesnt violate the restrictions mentioned
in the previous section.

However, if it\'s ever moved away, it will inflict a copy on us, so we
don\'t want to auto-inline things that are too large (perhaps 32b will
be the threshold?)

## Inlining for Statically-Sized Members

Note how Warrior contains a Pair:(Int, Int). We want that to be inline.
Just put inl in front of it:

> damageRange: inl Pair:(Int, Int);

This has a restriction: damageRange can no longer be moved, ever. Its
destructor must be called from inside Warrior\'s destructor.

> fn destruct(this: Warrior) {
>
> let :Warrior\[hp, str, Pair:(Int, Int)\[dmgMin, dmgMax\], weapon\] =
> this;
>
> }

Simplified:

> fn destruct(this: Warrior) {
>
> let \[hp, str, \[dmgMin, dmgMax\], weapon\] = this;
>
> }

Notice how we destructured the incoming pair immediately. This is
required for inline things.

Since it\'s inline, we cannot accept it like we normally could:

> fn destruct(this: Warrior) {
>
> let \[hp, str, damageRange, weapon\] = this;
>
> }

This is because we can\'t have damageRange pointing at the middle of the
now-dead Warrior\'s allocation. We needed to destroy it.

We could also copy it:

> fn destruct(this: Warrior) {
>
> let \[hp, str, copy(\_)(damageRange), weapon\] = this;
>
> }

### Auto-inlining

We don\'t auto-inline mutable things in structs, because we can\'t know
when there\'s any active borrows of the member.

We do auto-inline immutable things in structs, but very conservatively:
it must be very small (8 bytes, though we could raise that to 16 bytes).

In fact, this happened with hp; it was immutable data of 8 bytes, so it
was inlined.

## Inlining for Dynamically-Sized Locals

(Here dynamically means that we don\'t know it at compile time, but
it\'s a fixed size at runtime.)

Let\'s say that we had some subclasses for IWeapon:

> interface IWeapon {}
>
> struct Sword isa IWeapon { length: Int; }
>
> struct Spear isa IWeapon { length: Int; woodType: Int; }
>
> struct Glaive isa IWeapon {
>
> woodType: Int;
>
> woodLength: Int;
>
> bladeLength: Int;
>
> }

and we had a local IWeapon. We could apply inl to it:

> fn main() {
>
> let inl weapon: IWeapon = Glaive(3, 8, 3);
>
> }

One could look at this and think \"this lives on the stack.\" That\'s
mostly true. However, because dynamically sized stack frames have
drawbacks (\"Runtime stack size changes pretty much obliterate the
ability to stack walk. No exception unwinding, no debuggers, no crash
dumps\" -apoch).

So instead, we have a different stack, that lives in parallel to the
regular stack.

At it\'s core, it\'s really just a per-thread bump allocator.

### Must be Final

A dynamically-sized inline local must be final. We can\'t change it to
point to a different type, because that different type can be a
different size. We can\'t even change it to point to a different
instance of the same type, because things of the same type might be of
different sizes.

## Inlining for Dynamically-Sized Members

Remember how Warrior has a weapon: IWeapon. We can use inl to guarantee
that the weapon lives in the same allocation.

> struct Warrior {
>
> hp: Int;
>
> strength: Int;
>
> damageRange: Pair:(Int, Int);
>
> weapon: IWeapon;
>
> }

Whereas for normal statically-sized members we can put it directly into
the struct\'s memory, this will put it *after* the struct. The lowered
Warrior struct would be this:

> struct Warrior {
>
> int hp;
>
> int strength;
>
> Pair\<Int, Int\> damagePair;
>
> IWeapon\* weapon;
>
> }

and weapon points to just after the end of Warrior.

The benefits of this:

-   It reduces fragmentation.

-   It\'s more cache friendly, because when we read the field weapon to
    > get the address it points to, we likely just cached the pointee as
    > well.

A question arises: wouldn\'t we get this anyway, if our warrior was
allocated in a stack allocator?

No. We don\'t, because a stack allocator needs to be confident that the
lifetimes within are strictly non-increasing. If a Warrior has a
non-inline weapon, then we have no guarantee that that weapon will not
outlive the Warrior. However, by making it inline, we guarantee that the
lifetimes are the same, which means it can be used in a stack allocator.

A similar question: forget stack allocators, wouldn\'t we get this
anyway if we used a bump allocator? (An allocator whose pointer is
always increasing)

Yes, we would. However, bump allocators are rather niche.

Does it matter whether the weapon allocation before or after the Warrior
in the allocation?

Not really, because their lifetimes are the same.

### Dynamic Sizes

A struct that has a dynamically-sized member is itself
dynamically-sized. So, the entire previous section applies not just to
interfaces, but to structs as well.

Because of that, ~~any struct *could* be dynamically sized~~ any
**allocation** could be dynamically sized. In other words, just because
we\'re pointing at a Warrior, it doesn\'t mean the allocation is
sizeof(Warrior).

This could be troublesome. If we have a dynamically sized Warrior:

> struct Warrior {
>
> hp: Int;
>
> strength: Int;
>
> damageRange: Pair:(Int, Int);
>
> inl weapon: IWeapon;
>
> }

and we put one of those in an inline local:

> fn main() {
>
> let inl warrior = Warrior(10, 6, (5, 6), Sword(7));
>
> \...
>
> }

moving becomes complicated! Specifically, to avoid slicing the object,
we need to know its size.

For that reason, we\'ll put the size at the beginning of the allocation
(malloc does this anyway) and we can consult it when moving it around.

Also, keep in mind, this will be on the parallel stack, since it\'s
dynamically sized.

### Inlines\' Constructors Must Return Size

The constructor for a dynamically sized thing must return its size. That
way a containing struct knows where to put the next thing.

This constructor expects that it can use as much of the memory of its
given pointer as it likes.

We might need a different constructor that *doesnt* do this, which
instead uses regular malloc\...

### Must be Final

A dynamically-sized inline member must be final. We can\'t change it to
point to a different type, because that different type can be a
different size. We can\'t even change it to point to a different
instance of the same type, because things of the same type might be of
different sizes.

### Optimization

If a struct only has one dynamically sized member, then it can know that
it\'s at the end of the struct. We don\'t even need to store a pointer.

In fact, we can just always do this for the first dynamically sized
member. We get the first one for free!

## Unions

We don\'t have unions in the language, we instead have sealed
interfaces. We can inline these two different ways:

-   Using the inl keyword on the local or member, like described above.
    > Only works for finals.

-   Doing a C- and Rust-style \"max size\" approach.

The latter would mean that, for the IWeapon:

> interface IWeapon {}
>
> struct Sword isa IWeapon { length: Int; }
>
> struct Spear isa IWeapon { length: Int; woodType: Int; }
>
> struct Glaive isa IWeapon {
>
> woodType: Int;
>
> woodLength: Int;
>
> bladeLength: Int;
>
> }

we could have an IWeapon local or member, whose size is the maximum of
all the possibilities (24 bytes) plus an int to describe which it is (8
bytes), totalling to 32 bytes.

The benefit here is that it can be inline **and** varying. The previous
approach had to be final.
