jan 1 thoughts.md

Coord has three dimensions right now:
- ownership: Share, Own, Borrow, Weak
- region: empty for now
- kind: the type, which also knows whether its a primitive

right now, we assume anything with Share ownership is immutable.


option A: conflate region into ownership.
- re: ownerships:
  - Share is basically `rc`. Its region defaults to a region called DefaultSharedRegion.
  - Own doesnt have a region. It's basically an iso. (do we want to decouple this so that rcs can have iso?)
  - Borrow should have a path associated with it
  - Weak should also have a path associated with it
- region goes away
- kind stays the same


option B:
TLDR: CoordT(Ownership which includes Share, Path, Kind)
- re: ownerships:
  - Share is basically `rc`. Its region defaults to a region called DefaultSharedRegion.
  - Own doesnt have a region. It's basically an iso. (do we want to decouple this so that rcs can have iso?)
  - Borrow should have a path associated with it
  - Weak should also have a path associated with it
- region is known as a path now. it's either:
  - iso (declares a new path)
  - refpath (references an input group, and has a path to a subgroup)
  - refpath to a DefaultSharedRegion input group.
  In other words, there are two types of paths: input group, and declared group. we can also output declared groups.
  We could think of declared/output groups as input groups too, created by the callsite. that makes sense, the callsite
  will like that.
- kind stays the same

big open question: vale had multi-region data. do we want that? might be useful.
i suppose that's a case where Own wouldnt necessarily be an iso.
another reason it should decoupled.


option C: templar simplifies away Share somehow.
we could think of it as any other group really. after all, it does simplify to an ECS-like sort of group.
we could think of it as an Own.
this might be tricky because when we drop a reference, we dont necessarily destroy the thing.

option C1:
templar could conceptually... conditionally either call the destructor or give the reference to an invisible manager in the ether.
but maybe lowering to own is good because when we drop a Share, we kind of have to _assume_ we're destroying it.
for the templar to have that kind of logic baked in, it will need to know whether this is inherently shared or owned. i say that because even if we lower it to own, itll need to assume that it goes away.
however, i suspect this cuts off a certain option. what if a function takes in an RC parameter, and then clones the RC ref so now we have two RC refs pointing at the same thing, and then we conditionally drop one. it would be nice to know that the other RC still points to something live.
i suspect we'll run into this when we do anchoring.

option C2:
allow multiple Own references to the same object perhaps?
would any part of the templar that does own-handling need to care whether there are other own references too? thats the big question.
what effect (lol) does dropping an owning reference have? well, it invalidates any references that point into its group's child groups. that could indeed interfere with borrows that come from a different Own reference. it shouldnt, because we want to do anchoring.

option D: leave behind Coord, and go with onion typing.
- ownerships goes away.
- region also goes away.
- just left with kind.
- we add a new kind, called RefKind, that contains the region and whether its 
pros:
- not sure yet, but i bet there are some
- we get to specify the order of some of these things. e.g. Mutex<Arc<T>> is different than Arc<Mutex<T>>.
cons:
- means we cant compile to jvm.
- owned things probably still need a region.

# thought experiment: one reference owns, other references share?

what happens with SYO-like singly-owned RC objects like in shared-yet-owned?

re: option C
here, their sharedness is orthogonal to whether under the hood theyre shared. and weirdly, they dont invalidate share references.
can we have share references and own references to the same object? i think we can. weirdly, share references can only turn into a sort of borrow reference that cannot be mutated through, even though other references (from the own ref) can mutate the object.
how do we know that we cant mutate from a share reference?
we need some sort of system where a ref remembers not only the group it's pointing into, but also either:
- where this reference was derived from / came from.
- whether this is a readonly or readwrite reference, perhaps a field in Borrow ownership.
- some sort of way to tie an effect not to a particular group being modified, but the parameter provenance it came from too, and then the parameter/variable declaration will make sure that no modifications come in if it's from a Share.
this might have some overlap with how we'll handle anchoring.

also, all of this might be a reason to let the user specify mutation effects after the parameter itself like:
```rs
fn something(
    a: rc/Ship mut .engine
    b: rc/Ship
) {
    ...
}
```
this, combined with tracking parameter provenance, will help us reason across function boundaries whether mutations happen through certain parameters.


# thought experiment: what happens with anchoring?

For example this:

```rs
fn test_anchoring(s_0: share Ship) {
    // anchor
    s_1: share Ship = s_0.clone();

    // anchor
    s_2: share Ship = s_0.clone();
    s_2_borrow: &Engine = &s_0.engine; // s_2_borrow is a &Engine@s_0(rc/Ship)/engine

    // drop a_1
    s_1!;

    print(s_2_borrow.fuel); // should be fine because it comes from s_0/engine
}
```

This is nice because it means one reference can keep the object alive and therefore borrows derived from it alive, and it matches the user's intuition about what's going on.

something's iffy. do we need to communicate anchoring across function boundaries?
wait... if we do things across a function boundary, then it cant be dropped because the previous stack frame is frozen. so we really just need to remember that this is from something that cannot be invalidated.

so, maybe in parameters, we can just say that part of it cannot be invalidated no matter what else happens around it.

```rs
fn test_anchoring(s_0: rc Ship) {
    // anchor
    s_1: rc Ship = s_0.clone();

    // anchor
    s_2: rc Ship = s_0.clone();
    s_2_borrow: &Engine = &s_0.engine; // s_2_borrow is a rc/Ship/&Engine

    inner_func(move s_1, s_2_borrow);
}

fn inner_func(
    // own Rc<rc, Ship> because i'm having trouble expressing that we moved in a Rc from the
    // caller.
    // If we just cloned s_1 then it would be an &Rc<rc, Ship> which cant be dropped which wouldnt
    // illustrate what we want to here. thats why there's an own here.
    s_1: own Rc<rc, Ship>,
    // this live keyword says that we know this particul borrow ref will be alive; something's keeping it alive.
    s_2_borrow: live rc/Ship/&Engine
) {
    // Sends a mutation effect to rc/Ship, which would normally drop any rc/Ship&Engine
    s_1!;

    // But not s_2_borrow because there's an /_live/ in there which means that we have
    // something else somewhere that's keeping this thing alive.
    print(s_2_borrow.fuel); // successfully prints
}
```

So really, across function boundaries we just need to communicate that there's something keeping this thing alive.

Though, we still probably need to track intra-function that a reference came from s_2_borrow. Because, while things derived from s_2_borrow are guaranteed alive, no other Ship should be assumed still alive when we do s_1!.

im getting slight HGM scar spidey sense. but this just stays within the same group, so it should be fine i think.
this will work pretty nicely with final fields.

# thought experiment: immutable groups?

_live references get much more powerful when dealing with immutable groups. If we know that a group cannot be mutated through any other groups, then _live doesn't need final to keep borrows alive that point into child groups. or rather, one could say, when pointing into an immutable group, every reference is live, and it pretends every field is final.


# thought experiment: what happens with GR?

GR is weird. its like RC, but theres no share references, there are only weak references. we turn weak references into borrow references via a generation check.
- normal gen refs will just return a Option<&Spaceship> and the user can do what they want.
- probabilistic gen refs will do an .expect() on the resulting Option<&Spaceship>.


# thought experiment: what happens with GC?

GC is also kind of weird. its like RC, but there (DEFINITELY) arent any weak references. theyre all share. so i think it would all work like RC. these local variables are analogous to GC roots. i think they might even be GC roots literally, now that i think of it.

this kind of disagrees with C1 because C1 has a conditional here that conditionally calls a destructor.


# revisiting options

now that we have a clearer idea of what the system needs to be capable of...

it sounds like coord might need the path to contain information about which part of the path is live. WAIT. no. just whether the reference itself is live. we'll know from the path itself what's keeping it alive.
For example:
    live rc/Ship/&Engine
it must be because there's an `rc/Ship` rc reference somewhere in the caller.


option A:

not much changes about this, though the regions in the ownerships might need to be a bit more flexible.
there is still something kinda good about this option, in that it makes invalid combinations inexpressible.
but i guess that's the classic tradeoff of decoupling vs conflating. conflating means we can eliminate invalid combinations.
maybe we can get the best of both worlds, by conflating in the state, but then providing a decoupled view.

also, i think we'll want a readonly/readwrite aspect. in SYO, weak refs and share refs can become borrow references, through which we cant mutate.
presumably for option A that would be a field in Borrow, next to its path field.
Borrow will also want to track what variable it came from, to support anchoring. that would be in Borrow as well, so Borrow would be Borrow(path, permission), but path would contain 
a couple options:
- Borrow contains a boolean about whether it's `live`.
- Separate permission called `LiveBorrow` which cannot be invalidated.
The former makes a little more sense because `live` is kind of a minor detail that only affects invalidation, nothing semantic, and `LiveBorrow` would be otherwise very similar to `Borrow`.

option A still has one redeeming quality in that we can make unexpected states unrepresentable. not sure if thats enough of a reason to conflate some of these things though.


option B:

TLDR: CoordT(Ownership which includes Share, Path which includes live and var, Permission, Kind)
In other words, we add a Permission field to handle the SYO case.
this has another hidden benefit over option A in that we can express Readonly Share references and Readwrite Share references for even normal share objects. pretty neat.

this one's definitely still a contender.



option C1:

Own, which conditionally either calls a destructor or forgets.

that GC wouldnt work with this hints that we wouldnt want this option.


option C2:




option D:





# thought: simplifying

anchoring across function boundaries was tricky because we allowed moving a Rc into a callee.

if the caller doesn't clone the RC, then we'd run into the circuitous shapeshift.

if the caller automatically aliased any RC that a borrow was based on, would that help? probably would make it work, but that would mean that we need an RC increment every time we borrow something and pass the borrow into a callee. not very holy-graily.
