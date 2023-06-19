
# How Regions Are Lowered In Instantiator (HRALII)

(Note from later: this might be obsolete, we have heights now)

Note from later: we dont change the coord's ownership anymore, they stay borrow and share. To know the mutability we need to look at the current pure stack height and compare it to the coord's region's pure stack height.

Instantiator lowers region placeholders to RegionTemplata(mutable). However, this is the mutability of the region _at the time the thing was created_. A `List<mut&Ship>` is not a list of mutable references to ships, it's a List of references that it regards as mutable, but might not be mutable right now. Rather, it tracks what's mutable right now by putting that information in the coord's ownership, which can be owning, mutable borrow, or immutable borrow.

The instantiator is in a constant quest to simplify coords to just two things:

 * Ownership: owning, mutable borrow, immutable borrow.
 * Kind. Any regions in the template coords are reduced to booleans.

```
func main() { // main'
  ship Ship = ...;
  // Typing phase: own main'Ship<main'>
  // Instantiator: own Ship<mut'>

  list List<&Ship> = ...;
  // Typing phase: own main'List<&main'Ship<main'>, main'>
  // Instantiator: own List<mut&Ship<mut'>, mut'>

  pure { // p1'

    z = &list;
    // Typing phase: &main'List<&main'Ship<main'>, main'>
    // Instantiator: imm&List<mut&Ship<mut'>, mut'>

    listOfImms List<&main'List<&main'Ship>> = ...;
    // Typing phase: own p1'List<&main'List<&main'Ship<main'>, main'>, p1'>
    // Instantiator: own List<imm&List<mut&Ship<mut'>, mut'>, mut'>
  }
}
```

When the instantiator enters a pure block, **the coord's ownership is different.** The:

When you ignore all those irrelevant default region `mut'` things out, a:

`&main'List<&main'Ship<main'>, main'>` becomes:

`imm&List<mut&Ship<mut'>, mut'>`.

Note how the ownership is `imm&`.

However, **it doesn't actually see the surrounding kinds as different. The kinds are the same.** Think of the kind's template args as a snapshot of how the kind was made.

When you ignore all those irrelevant default region `mut'` things out, a:

`p1'List<&main'List<&main'Ship>>` becomes:

`own List<imm&List<mut&Ship>>`.

Note how the `mut&ship` is still `mut`. Rule of thumb: think of a kind's template args as how it sees _itself_. It doesn't mean it's mutable to us; we'd have to traverse through an immutable ref to get to it, and viewpoint adaptation keeps it immutable to us.


Also note how the **coord has no region information anymore**. They still appear in template args, but only as a mutable/immutable boolean. 


Other notes:

 * Yes, it's weird that every struct has a mut' at the end. It makes sense, you can only create a struct in a mutable region, and a struct's template args are a snapshot of when it was mutable.
 * In the above snippet, we say a simple `Ship` is an `own main'Ship<main'>`. That's because we still haven't decided whether the region should be in the kind only or the coord too. It seems orthogonal to this topic, luckily.
 * We do in fact need a mutability boolean in RegionTemplata. "What if we instead relied on the ownership part of the coord to distinguish between various instantiations with different region mutabilities?" Because if we have a `struct Moo<z', ShipKind> { ship z'ShipKind; }`, where ShipKind is a kind, we can't distinguish between a `Moo<imm', ShipKind>` and `Moo<mut', ShipKind>`. There are likely other hidden differences mutability can cause too.
 * (We probably shouldn't reuse the coord's region to store mutability, as that's used to describe what region a string is in. Let's tackle that problem separately.)


As you'd expect, a `LocalLoadHE` inside a `pure` block that loads from an outside local `mut'List<&mut'Ship>` will produce an `imm&mut'List<&mut'Ship>`.

Similarly, if we hand in a `mut'List<&mut'Ship>` to a pure function, it should receive it as a `imm&mut'List<&mut'Ship>`.


Alternative: Instantiator makes RegionTemplata(initialMutability, stackHeight). This isn't great because then we'd need to cast when we're calling a pure function.


### Rejected Alternative: Templar calculates mutability for LocalLookup

In the templar, we use the stack height to determine whether a given LocalLookup is loading from a now-immutable region.

Instantiator will then read it, and that's the only time it ever produces an immutable borrow for a coord ownership. If we hand an immutable borrow in to a regular borrow, we produce another immutable borrow, kind of virally.

The downside of this is that the instantiator has to inspect the incoming coords to an instruction to see if something is immutable.


### Rejected Alternative: Templar makes immutable borrowed coords

We can make the Templar populate coords with "immutable borrow" ownership. This is mostly when inside a pure block we read a pre-pure local, or when we're calling a pure function.

This would make the typing phase a little more complex. We now have immutability information in three places: ownership, region in coord, region in kind.

The advantage is that the instantiator doesn't have to calculate the immutable ownerships itself. Except it kind of does... when it sees an immutable borrow being handed into a readonly borrow, it'll have to produce another immutable borrow virally. Since it would have to do that anyway, this is no better than approach B.


### Rejected Alternative: RegionTemplatas contain originalMutability and stackHeight, like PlaceholderTemplatas

This doesn't quite work.

`main` might call it with a RegionTemplata(8, false), `bork` might call it with a RegionTemplata(6, false), and all sorts of other functions will be handing in different stack heights, different templatas. This means the instantiator will create way more versions of each function than we want. Additionally, those incoming stack heights make no sense for `printShip`. The instantiator will see it and be confused because they don't line up with the current function.

Instead, we could make it so **callers hand in 0 for the stack height** into function calls. In a way, we're creating a new region in a new world. It's kind of like we're creating a virtual region.

This would be done in the instantiator so the typing phase doesn't have to worry about any of this. It just checks mutabilities line up with what the callee expects and is done. The instantiator however has to do some special logic.

When the instantiator sees that a caller is doing a `pure` call, it changes its substitutions. RegionTemplata(8, true) becomes RegionTemplata(0, false).

The downside is that, we'll need to do some casting so the backend understands that we can hand in a `Ship<RegionTemplata(8, true)>` to a function that expects a `Ship<RegionTemplata(0, false)>`. The accepted solution doesn't have this weakness.


### Rejected Alternative: Update Template Args According to Current Mutability (UTAACM)

For example, a pure block wouldn't see a previous `&main'List<&main'Ship>` as `imm&List<mut&Ship>`, it would see it as `imm&List<imm&Ship>`. After all, that ship is immutable to us right now.

This seems to always end up needing some casting somewhere, especially in the backend. The accepted solution needs no casting in the backend.


### Rejected Alternative: Change Substitutions, Erase Regions from Types

(build on UTAACM)

Perhaps we could have something (a `pure` block perhaps) to signal the instantiator to change its substitutions. Instead of main' -> RegionTemplata(true), it needs to have main' -> RegionTemplata(false) for a certain scope. Then, it can call the receiving function that expects a RegionTemplata(false).

However, then a variable might have a different type inside the block than outside:

```
struct Ship { hp int; }
func main() {
  ship = Ship(42);
  // ship is a coord (mut main)'Ship<(mut main)'>
  pure {
    // ship is a coord (imm main)'Ship<(imm main)'>
    println(ship.hp);
  }
}
```

...which would confuse the backend when we try to read from ship. It would expect one type, and see another. So, we really shouldnt have the mutability appear in the templata.

Instead, let's erase all regions from the types, probably in instantiator. All RegionTemplatas for types are rewritten to true, as if they're mutable. Instantiator can say at an expression level whether to skip gen checks or not.

Three remaining minor downsides:

 * The typing phase itself will have trouble comparing the two. Let's say we return a reference from the pure block, we'd have to do some tricky logic to compare the result `(imm main)'Ship<(imm main)'>` can be received into a `(mut main)'Ship<(mut main)'>`.
 * The instantiator doesn't have enough information to sanity check the typing phase's outputs. That'll be nice to have when we distribute .vast files with the typing phase outputs.
 * We have to loop over the entire environment. Not too expensive, but the other approach is a bit faster.


# Can't Translate Outside Things From Inside Pure Blocks (CTOTFIPB)

(Note from later: we found a way to resolve this, see TTTDRM)

In this snippet:

```
struct Engine { fuel int; }
struct Spaceship { engine Engine; }

exported func main(s &Spaceship) int {
  pure block { s.engine.fuel }
}
```

Before the pure block, `s` is a `&main'Spaceship<main'>`, instantiated it's `mut&Spaceship<mut>`.

From inside the pure block, `s` is still a `&main'Spaceship<main'>`, but instantiated it's a `imm&Spaceship<mut>`. Note how only that first `mut` turned into an `imm`, because of HRALII.

However, the implementation can be trippy here. In the pure, when we do a LocalLookupTE, it contains a `ReferenceLocalVariableTE` which contains the type of the local, in this case `&main'Spaceship<main'>`. Of course, when we do the instantiation, we see `main'` is immutable, so it erroneously produces `imm&Spaceship<imm>`.

The answer is for the instantiator to keep track of the current type of every variable in scope, and not re-translate it inside the pure block like that.


The same applies to structs. ReferenceMemberLookupTE.memberCoord might be translated as `imm&Engine<imm>` when it needs to be `imm&Engine<mut>`.


The same applies to when we're translating anything from outside the pure block. In LocalVariableT, we have the full name of the variable, which includes the full name of the function. When we try to translate it, suddenly its mutable parameters are immutable, and the variable suddenly thinks it's in a different function.

So generally speaking, we shouldn't translate anything outside the pure block from within the pure block.

(Note from later: we found a way to resolve this, see TTTDRM)


# Time Travel To Determining Region Mutabilities (TTTDRM)

(Note from later: this might be obsolete, we have heights now)

Recall HRALII, where a template argument that's a region becomes the mutability at the time the template was created.

Of course, that's difficult to do. If we mess it up we run into the CTOTFIPB problem.

Let's take this example:

```
struct Spaceship { fuel int; }

exported func main(s &main'List<&main'Spaceship>) int {
  pure block {
    z = s;
    z.engine.fuel
  }
}
```

`s` is a `mut&List<mut&Spaceship>`, but `z` needs to be an `imm&List<mut&Spaceship>` However, if inside the pure block we just use the current mutabilities of the `main'` region we erroneously end up with `imm&List<imm&Spaceship>`.

What we really need is to go back and calculate the mutabilities at the time the `List` was made.

Luckily, there's an easy way to do that. We just calculate List's template args' mutabilities **from the perspective of List's region**.

In other words:

 1. We know that `List` is in the `main'` region which is currently immutable, so it's an `imm&List<something>`.
 2. Now we start processing `List`'s template arguments there. But we do it **from the perspective** of `main'`. This is the "**perspective region**".
 3. We encounter the `&main'Spaceship`, and ask, "what is the mutability of `main'` from the perspective of the containing List?" in other words "what is the mutability of `main'` from `main'`?" And the answer is, of course, mutable.

So how do we calculate the mutability of one region from the perspective of another?

Conceptually, we need to figure out if there was a pure block introduced between those two regions. Instead of searching through the environment, let's just have RegionPlaceholderNameT **remember the location of the latest pure block** from its perspective. That way, we can just check if the latest pure block is before or after the other region.



# RegionTemplata Has Pure Stack Height (RTHPSH)

RegionTemplata(boolean) didnt quite work, because we couldn't determine (from just regions' booleans) the relationship between all the regions. We need to know what region is pure from what region's perspective, not just whether a region is mutable or not. When a function can only receive one boolean per region, it can't tell if one immutable region is immutable to another immutable region. (DO NOT SUBMIT TODO explain this more)

So instead, we'll have a `RegionTemplata(Option[Int])` where the int is how many pure blocks between here and there. Zero means mutable. One means there's one pure block between us and this region, and two means there are two pure blocks between us and this region.

If x' is 1 and y' is 2, then both are immutable, and y is immutable to x.

If x' is 1 and y' is 1, then both are immutable, and y is mutable to x.

None means that we don't know yet, because it was a region that was handed in and we don't know whether it's mutable or how immutable it might be.



# Region Generic Params Pure Heights Are Some Zero (RGPPHASZ)

(Some Zero means `Some(0)`)

A few places in the typing stage will need to know whether a region is mutable or not.

We could have put a `initiallyMutable` boolean in `RegionPlaceholderNameT` but it seemed nice to just use `Some(0)` for the pure height instead. It does however mean that not all region generic params are None.


# Instantiator Collapses Region Heights (ICRH)

When we're in a function in the instantiator, we might have a reference to a 7'List<5'Ship>. In a different function in the instantiator, we might have a reference to a 4'List<1'Ship>.

Both of those want to call List.add. So we might stamp a version of add for 7'List<5'Ship> and a version for 4'List<1'Ship>.

But wait, that would lead to a codesize explosion; there are two add functions. It's especially ironic because those two Lists have identical layouts: they're both just a list of immutable references.

So it would be nice to have those call the same function.

However, we don't want to confuse the backend. The first one might be mangled to be List_Ship_5_7 or something and the second one might be mangled to List_Ship_1_4. The exact names don't matter, but what matters is that they're different. We'd have to do some casting in the backend, which seems... unfortunate.

The answer is to have a distinction between a "subjective type" (7'List<5'Ship>, 4'List<1'Ship>) and a "collapsed type".

The collapsed type is something they can both simplify to, in this case `0'List<-1'Ship>`. Basically we only use contiguous integers, in the same direction relative to each other. -1 is less than 0 in the same way that 5 is less than 7 and 1 is less than 4.

We use negative numbers just as a convention: negative for function incoming regions that are immutable, zero for mutable at call-time. The default scope for the function will then be zero, which seems nice.

## RegionCollapser (ICRHRC)

Note that when we create the actual types in the instantiated AST, a lot of the regions are lost. They only seem to appear where there were explicit lifetime parameters, like in `func moo<x', y', T>(...)` etc (including the default region). I'm not sure if this will lead to collisions in the backend, or if it happens to work theoretically perfectly to collapse a bunch of equivalent functions.

In RegionCollapser, we recursively collapse things. First we establish ("count") a region map, for example (7, 5, 7, 0) becomes (7 -> -2, 5 -> -1, 0 -> 0). We then use that for our *immediate* members, like the ownerships of coords and the values of RegionTemplatas. We dont use it for anything else, we let everything else under us do its own counting. Not sure if this is the right way to go though, it might be skipping some important steps and losing some detail. It's hard to say.

Note from later: Wait, it seems like we don't really do it for coords, because those come in with their ownership already figured out. It looks like we're doing it for just things with generic parameters.


##

we want to be able to do some mapping if we can. that might be hard though. we have to 

we need to reduce the things that we're sending in for evaluation. reduce them when we send it in for evaluation. just for naming purposes. everything else will still in parallel do the collapsing.

interestingly, collapsed *is* enough for naming purposes. its still unique.
but we do need the reduced version for kicking off the right stuff for the new instantiations.



# Ignore Previous Ownerships Mutabilities From Instantiated Coords (IPOMFIC)

When we have a substitutions array that contains, for example, `E = -1'*#Thing` (an immutable share reference to something in region height -1), that substitution was calculated from the perspective of the containing function.

But if we have an array of them, like `-1'[]E`, then when we substitute it, it really needs to be mutable again.

So, when we're substituting that `E` into that `-1'[]E`, we ignore whatever ownership mutability it had and just recalculate it (in this case, it will be a mutable share, `*MyThing`).



# Handling Collapsed Coords and Subjective Coords Simultaneously (HCCSCS)

If we want to avoid any casting in the backend (see ICRH), then we have to do the region collapsing eagerly.

The naive approach would be to collapse all regions eagerly when we create the instantiated nodes. However, that can lead to some confusion later. For example, when we translate a FunctionCallTE, we translate its arguments and look at the result types. Those result types, if collapsed already, could be referring to nonsense regions; if our function has subjective regions 0 1 2 3 4 5, the collapsed nodes might refer to collapsed regions -2 -1 0 which makes zero sense to the function. The function has no idea what those regions are.

We can't just look at the typing phase to know the regions either... those often contain None and defer knowledge of the actual region to the instantiator (which combines the typing phase's RegionPlaceholderNameT with the actual function argument RegionTemplataI, and the only reasonable combination of those data is instantiated nodes).

So we have a bit of a conflict. The mere act of figuring out something's actual subjective regions produces an AST... but we want a _final_ AST with collapsed regions.

The next naive approach would be to just have two separate compiler stages. First we produce an entire program with all subjective regions. Then we take that result and collapse everything down to collapsed regions. However, this doesn't work: we'd run into a code size explosion without collapsing.

So they need to happen at the same time.

The next approach would be to have the AST just contain both. This works, but it's a little awkward to have internal implementation details (the subjective regions) in the resulting AST when the receiver (the backend) doesn't care about them.

The final approach is to make it so the resulting AST only thinks in terms of collapsed regions, and takes things in via constructor to never calculate them on their own. The instantiator will have to produce its own correct temporary information (such as an extra return from translateRefExpr) and also be responsible for populating the collapsed regions into the final AST.


# Backend Doesnt Care About Regions (BDCAR, AITANR)

Under the hood, the instantiator is really just trying to figure out what references are currently immutable. The RegionTemplata(int) is mostly just an abstraction that helps humans reason about it better.

The backend doesn't know anything about regions. It just knows about ownership, for example immutable_borrow vs mutable_borrow.

The frontend might know about `x'List<y'Ship>` which might lower to `1'List<0'Ship>` and `0'List<0'Ship>` but it's responsible for assembling those into two completely distinct types that are completely separate from the backend's perspective, for example `List_Ship_-1_0` and `List_Ship_0_0`.

This doesn't seem to be a hard requirement, but it makes the backend pretty clean.

Well, there is one corner case. One thing regions helps with that coding with `imm&` and `&` can't do: it helps the compiler know when it's safe to cast from `imm&` back to `&`, such as when we come back from a pure block. The compiler knows that if a pure block is returning something created just before itself, it can safely cast that to a mutable reference... but if a pure block is returning something created three pure blocks up, then it shouldn't cast that to a mutable reference. The instantiator is responsible for figuring this out and generating Mutabilify nodes.


# Can Cast From Collapsed To Subjective (CCFCTS)

A collapsed function will have region templatas containing negative numbers or zero (the default region is zero). From there, it can start using those negative numbers and zero just fine as subjective regions. When we hit the first pure block, we're then at height 1, and so on.

So, when we start translating the function, we can treat the collapsed regions as subjective regions.

In the code, this can literally be a type-cast, because the AST with collapsed regions is the same as the AST with the subjective regions, just with a different zero-size generic arg.

