Builds upon [HGM V22](HGMv22.md)'s option A.

# HGM V22's Problem

It isn't a problem per se, but it's an unfortunate bit of overhead that HGM globally puts on the system, even when HGM isn't used. It makes it not a zero-cost abstraction.

Specifically, a borrow ref in HGM is four things:

* Pointer to the object
* Offset to the generation
* Remembered generation
* Scope tether bit mask

When we use a borrow ref to get a struct's inline member, we need to:

* Add to the object pointer
* Subtract from the generation offset
* (Do nothing to remembered generation)
* Shift the bit mask

That's normally fine, except **it also happens for immutable region references** because when a pure function returns a borrow ref, it has to have all that information if it's to be a real borrow reference. And unfortunately, none of these can be calculated from any others.

In other words, HGM's overhead is also present when we're doing immutable region borrowing.

It's not a dealbreaker... in the hands of a master, if HGM is used enough, it could be worth it. But we shouldn't need to be a master to take advantage of Vale's speeds.


# Solution

Let's take a few steps backwards. Let's opt-in to tethering per-type (or even per-coord, worth considering) with a `tetherable` keyword on the struct:

```
struct Spaceship tetherable {
  engine Engine;
}
```

And then we can have `live` references to it.

`tetherable` means that we'll always have some bytes before the instance where we can find its scope tether, which is either a 2-byte RC or a scope tether.

If the object already has a generation (such as if it's a stack allocation, heap allocation, or array element, inline varying struct with owning ref, inline enum) this won't cost us anything. It will otherwise cost us some space for some inline structs though, which is unfortunate.

This could fit pretty well though. It's pretty likely that most tethering will happen on arrays (for ECS) and on top level denizens (in OO) so it might work out well.
