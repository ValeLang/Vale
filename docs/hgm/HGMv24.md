
We've mostly obviated HGM's need by using regions. See [this page](https://verdagon.dev/blog/zero-cost-memory-safety-regions-overview) for more.


There is still some desire for HGM though, for something that can gives us more precise control within a region for eliminating gen checks.


Consider this function:

```
func makeBoard(rand_seed int) [][]bool {
  rows = [][]bool(20);
  foreach row_i in 0..20 {
    row = []bool(20);
    foreach col_i in 0..20 {
      random_bool = true;
      row.push(random_bool); // making this & could cause a gen check. hmm.
    }
    rows.push(row); // same here.
  }
  return rows;
}
```

That `row.push(random_bool)` is feeding a non-owning reference into the `push` function. Inside the `push` function, it doesn't know whether that non-owning reference is guaranteed to be alive.

There are a few options here.

## A: Inline

If we inline the `push` function, then Catalyst can tell that the owning reference is still in scope.

This _seems_ like a rather limited approach because we can't inline everything. That's partially true.

I suspect a lot of cases are like this though: taking a non-owning ref to an owned value and handing it to a small function. This one small measure really could surprise us.


## B: Simple Live Refs

We add live refs in. Rules for making a live ref:

 * We can make a live ref from an owning stack reference. Zero cost?
 * We can also make a live ref from an heap-owning reference. Zero cost?
 * We can make a live ref from a non-owning reference's heap-owning reference. We'd tether the heap allocation's bit.
 * We cannot make a live ref from a non-owning reference's inline-owned reference. We wouldnt know where the bits are.
 * We can however make a live ref from a non-owning reference's inline-owned cell. We'd lock a bit on the cell.
 * We _can_ make a live ref from an existing live ref's final inline-owned thing.


Perhaps instead of needing a cell, we could have a keyword for adding a specific generation for an inline field. Then we could use its bit for the locking.


## C: Monomorphizing Live Refs

We add `live` in as a keyword on a parameter. We then monomorphize the function: one for when we know it's alive, and one for when we don't.


This is a little better than inlining because LLVM can still decide whether it's worth it to inline. If not, monomorphizing can actually be _better_ for code size than inlining.


## D: Add-only Regions

We can mark a function as "add-only", meaning we can't destroy anything from any region that's passed in.

This would work well for arrays, and possibly for setting Nones to Somes (though we are conceptually getting rid of a None, so maybe not).

It wouldn't quite work well for hash maps or array lists, because adding sometimes moves things around. So this doesn't seem like a very useful solution.


## E: Uni

This sounds okay, but it would likely need overloads or monomorphizing so we could use these functions elsewhere.


## F: Trackable, Per Type

If we call a type `trackable` then we'll put a boolean somewhere inside it. Then we can always take a live ref to it.

We'll usually try to squeeze it into padding, or into the high bits of a pointer.

If it doesn't fit, we'll probably put it _before_ the struct. That way it can overlap nicely with a parent struct's generation, or a preceding struct's padding.

We could combine `trackable` with `cell`. `cell` can have a live bit for this, a writing bit, and an immutable bit.


If we want to give better control, we can say `cell auto`.


## G: Trackable, Per Instance

We could say `a trackable List<int> = ...` and then itll have some bits somewhere, and we can make `live` references to it. 


## Conclusion

We'll start with A.

D doesn't work.

B would be good, except we won't be able to use a non-owning reference to reach an inline-owned list and `push` to it. That's not great. C could augment this well.

Let's not do E. C seems strictly better as it's a little easier to know whether we own something than whether its unique.

C, B+C, and G all involve some user intervention and decisions. Extra complexity. That's not _bad_, but it would be nice if we could avoid it.

F seems pretty simple. Let's add that after A.

We could also add G in, so that even if a type isn't inherently `trackable`, we can override it to make it trackable. That seems like the best of both worlds.

TL;DR: *A, then add F, then add G.*
