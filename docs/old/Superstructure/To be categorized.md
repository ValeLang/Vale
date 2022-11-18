impl Marine for *\[Marine?\]*ChronoRef {

fn mut hp(self, hp: Int) {

let marine = self.root.root!.marines.get(self.id);

if (marine.\_\_version == self.root.version) {

\_\_unsafe_mut marine.hp = hp;

} else {

let newMarine = Flat:Marine(self.root.version, hp);

self.root.root!.marines.put(self.root, newMarine);

}

}

}

**Fat pointer and implementor musings**

a fat pointer represents an aspect or role, more than an interface.
Minalan has many roles: Archmage, Magelord, Master, and GetTitle() might
do something different depending on which role someone knows him as.

So, why does a fat pointer have to contain an object pointer? Why can\'t
it contain an int? A chronoref? void? We could.

Let\'s refer to this concept as a \"flexible fat pointer\".

**Flexible Fat Pointer via Coord Subclass**

If we wanted to support more kinds of smart pointers, we could have
Coord have an abstract getRawPointer() method that returns an AST, and a
getContainedType() method.

-   For regular pointers, getContainedType() will return self, and
    > getRawPointer() will return an AST getting self.

-   For chronorefs, getContainedType() will return the chronoref (root +
    > id), and getRawPointer() will return an AST calling into the root
    > to get the raw pointer.

-   For ECS, it can contain a pointer to the pool, and an int id.

-   For a global arena, it can contain just an int.

I prefer the next approach, finalimplementor.

**Flexible Fat Pointer via finalimplementor**

So, we need to signal to the language that \"everything that implements
IUnit will have exactly this thing inside it.\"

struct ChronoRef {

root: &Root;

id: Int;

}

interface IUnit {

finalimplementor ChronoRef;

fn shoot();

}

struct MarineChronoRef implements IUnit {

ChronoRef ref;

fn shoot() {

// call into the shoot() superfunction

}

}

the finalimplementor must either be imm or have copy semantics.

on JVM, we can ignore finalimplementor. on CLR, we might as well, since
all interfaces are references there anyway.

**ChronoRef**

How do we reduce the number of indirections to use a chronoref?

Well, same as the fat pointer approach, we want to bring as much stuff
into the pointer as possible.

There are two parts of a Marine chronoref:

-   (8 bytes) root: &Root;

-   (8 bytes) id: Int;

if we had an IUnit pointing at a MarineChronoRef, we would want three
things in it:

-   (8 bytes) root: &Root;

-   (8 bytes) id: Int;

-   (8 bytes) IUnit itable, in this case, the Marine as IUnit itable

in fact, every IUnit will always look like that.

Every interface chronoref will contain those first two things.

just use finalimplementor to make this happen.

these ChronoRef should have copy semantics. that\'s good, since they\'re
smart pointers, and we want them to maintain ref counts on the original
superstructure.

**Can we borrow a ChronoRef?**

No. Not sure if we want to say that nothing with copy semantics can be
borrowed, but in this case, we sure dont want to be able to borrow a
ChronoRef.

**Inside superfunctions, can we have a hidden first Root pointer?**

No. Inside the superstructure, we need every single chronoref to have a
root pointer. That\'s because a superfunction can be dealing with
multiple snapshots and forks, and each ref needs to know what it\'s part
of.

**How do we do the Chronobase plugin?**

Approach A:

ChronoRefs should probably just look like regular pointers to the
templar. We\'ll mark all structs with some sort of internal annotation
to say that they\'re chronorefs. Post-hammer, we\'ll do some huge
transforms on the resulting superfunctions, and on all the functions
outside that use references pointing into a chronobase.

Approach B:

The plugin will turn the node structs into interfaces, for example,
MarineChronoRef (with hp, strength, etc) would become IMarineChronoRef
(with getHp(), getStrength(), etc). Later, the plugin will implement
those interfaces with only one subclass, in this case, MarineChronoRef
(with root, id).

This is nice because we don\'t have to mess with any functions outside
the superstructure.

Approach C:

Make a Coord subclass. Dereferencing, updating, etc. will put in the
right little ASTs.

Conclusion: I prefer approach B.

lets combine fork() and snapshot(). their only restriction is in revert
anyway.

that way, we can just return owned everything. it\'s all the same
struct.

\@History - always an imm list of effects (and/or requests)

\@Chronobase

\@History(Chronobase(Infinite))

\@History(Chronobase(100))

\@History(Linear(Infinite))

\@History(Linear(100))

keep a list of additions and removals for reverting.

The borrows can just be a per-root hash map. doesnt need to be a
persistent hash map either.

How do we do immortal objects? In other words, things outside the
chronobase that the inside-chronobase things can point to.

This might be generalized to: things inside the chronobase can point to
anything with a lifetime greater than the chronobase.

Should we coalesce adds? for example, ss.things.0 and ss.things.0.blork
and ss.things.0.moop. we could combine them all. in fact, i think we get
that for free by default.

**Lessons learned from Incendian Falls:**

Bunches are amazing.

We were terrifyingly close to putting components of one incarnation into
the components of another incarnation. We luckily had a check to make
sure that the roots agreed before setting something.

Weak pointers are a lot more useful than I thought they would be, like
wow. Especially for iterating over something without modifying it
concurrently.

We had to reconstruct the views when we reverted. An unexpected cost. We
also had to do comparisons of everything that changed during a revert.
These can probably be combined.

See Incarnation Mutability Challenge.

We definitely want only a subset of the document to be journaled,
because:

-   We need a place to put hash maps and views

-   We need a place to put all the past versions of things.

We want transient structs, things that are used in calculations and not
stored. This ties in nicely with structs that arent journaled, that\'s
basically what they are.

See Metastate Challenge.

It was extremely useful to know the state of a thing before its backing
instance was deleted; this meant we could see the events (in events
list) that led to its death.

Before the next request, we want to delete events, and delete
alive=false things. Interesting pattern there.

See Deletion Challenge.

We needed to somehow signal to the user that a unit was attacking
another unit. We needed events for that. Events are probably best done
manually.

We needed to show the intermediate state between enemies attacking;
needed to pause the world while something was attacking.

See Intermediate State Challenge.
