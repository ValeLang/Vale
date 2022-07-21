**Goal: Combine normal references (aka \"pointers\") and borrow
references (aka \"borrows\"),** so that life is easier for the user, and
we don\'t bifurcate the codebase.

But still allow them to make a non-tethering local, just in case we
*want* the thing to be deleted, and for finer-grained control, perhaps
for lists.

Preferably:

-   It needs to be *very* predictable, because a misstep could cause a
    > runtime crash.

-   Avoid cases where if we change something on line X, it causes a
    > crash on line Y.

## Edge Case 1

Return a borrow reference, then run something else, then feed it into a
function call.

In other words, holding a returned borrow reference in a register while
other code runs.

> fn foo(x &X) &X { x }
>
> fn bork(notether a &Ship, b Ship) { \... }
>
> fn main() {
>
> x = Ship(7);
>
> bork(foo(&x), unlet x);
>
> }

Risk: If we eagerly tether the result of foo(&x), then unlet x will
crash..

Solutions:

A.  Don\'t eagerly tether something that\'s headed for a noborrow.

B.  Have all returns be pointers.

C.  Eagerly release tether before other code runs.

D.  Have an error when handing a borrow into a pointer. More on this in
    > Edge Case 2.

**For now we\'ll go with D.**

## Edge Case 2

Like the above, but someone refactored that expression into an
intermediate local\...

> fn main() {
>
> x = Ship(7);
>
> z = foo(&x);
>
> bork(z, unlet x);
>
> }

\...which causes a crash, because z is now a named local with a tether.

**Solution A:** See that z is only fed to notether arguments, and make
it notether automatically.

**Solution B:** Require a keyword to transform a tether into a notether.

> bork(**untether** z, unlet x);

This will bring attention to the area with the problem\... but not
really solve it. z is still tethered, and will cause a crash on the
unlet x line.

**Solution C:** Throw an error if a tethered local is given to a
notether argument. Fix becomes:

> **notether** z = foo(&x);
>
> bork(z, unlet x);

This is nice because it helps propagate notethers upward to the root
local. Bonus: it\'s easy to accomplish solution B with this mechanic.

**For now we\'ll go with C.**

## Edge Case 3

We actually have some freedom in determining where we\'ll turn pointers
into borrows.

If we have some returned borrow refs, and we know we should tether them
before we hand them off, we have a choice on when to tether.

> fn foo(x &X) &X { x }
>
> fn main() {
>
> x = Ship(7);
>
> z = foo(&x);
>
> // ← option 1
>
> aPureFunction(\...);
>
> // ← option 2
>
> unlet x;
>
> // ← option 3
>
> bPureFunction(\...);
>
> // ← option 4
>
> cPureFunction(z, \...);
>
> // ← option 5
>
> dImpureFunction(z, \...);
>
> }

1.  Could tether z here. unlet x; would crash.

2.  Could tether z here. unlet x; would crash.

3.  Could tether here, would crash here.

4.  Could tether here, would crash here.

5.  Can\'t tether here, because cPureFunction(z, \...) will read z and
    > cause UB.

It seems the rule is: **we can turn a pointer into a tether at any time
before it\'s used.**

For now we\'ll immediately tether it when it hits the variable, we can
explore optimizations later.

## Edge Case 4

Let\'s introduce the notion of \"raw borrows\", it\'s where we don\'t
have an active scope tether on something, and it might disappear at any
time.

For example:

> fn foo(x &X) &X { x }
>
> fn main() {
>
> x = Ship(7);
>
> z = foo(&x);
>
> // ← option 1
>
> aPureFunction(\...);
>
> // ← option 2
>
> unlet x;
>
> // ← option 3
>
> bPureFunction(\...);
>
> // ← option 4
>
> cPureFunction(z, \...);
>
> // ← option 5
>
> dImpureFunction(z, \...);
>
> }

z can be a \"raw borrow\", we don\'t have to grab a gen or do a scope
tether on it because we see it obviously comes from x, which is still
alive.

However, we\'ll want to turn it into a pointer or a borrow at 1 or 2.
Really, **we must turn any raw borrows into real borrows before any
unlets.**

Here, its original scope is inside our function. But, it was tethered
before our function was called, we can keep using the raw borrow.

For now we\'ll not do this, but later on when we explore optimizations
we\'ll likely tether it at 2.

## Conclusion

Lets verify this for a while, manually do the conversions, with our
borrow and pointer. Make sure we get the mechanics down right. Let\'s
ignore raw borrows for now, they\'re kind of an optimization over
borrows.

For edge cases 1 and 2, it means we only convert between pointer and
borrow while making a new local.
