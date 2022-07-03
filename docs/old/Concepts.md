# Concepts

Concepts are really hard (CARH). This doc describes what would happen
implementation-wise.

TL;DR: the compiler has to be able to:

-   Merge rules from different areas and shake it to see if it all fits
    > together.

    -   \...without any rune collisions\...!

-   Figure out a given function\'s return type given some parameter
    > types (so basically compiling the damn thing).

## Simple Example

Let\'s say we wanted to do this:

> let myArr = Array(5, { \"elem\" + \_ });

\...in a nice type-safe way.

We\'d have this Array function:

> fn Array(n: Int, generator: #F) Array\<#T\>
>
> where (#F like fn(Int)#T) { \... }

\...which expanded a bit looks like:

> fn Array(n: Int, generator: #F) Array\<#T\>
>
> where (#F like IFunction1\<mut, Int, #T\>) { \... }

First, we\'d hand in main:lam1 as the #F. Then it would ask \"does
main:lam1 conform to IFunction1\<mut, Int, #T\>?\".

To do that, it would look at IFunction1\...

> interface IFunction1\<Mutability#M, Ref#A, Ref#R\> #M {
>
> fn \_\_call(this, n: #A) #R;
>
> }

\...and combine its rules into our universe.

It would then start the \"searching\" phase, where, for each method
(there\'s only one) it will look for a similarly-named method in
main:lam1\'s environment, and merge its rules into our universe, and see
if we can infer anything. If so, we throw away those rules, but keep the
conclusion, and also note that we found that method.

For example:

-   It would look for all the \_\_call in the environment, and find the
    > one from the lambda { \"elem\" + \_ }, which is more like fn
    > main:lam1(this, x: #X){ \"elem\" + x }.

-   It would somehow try lining up the arguments with the
    > IFunction1.\_\_call, and see that its Int corresponds to the
    > lambda\'s #X, which corresponds to #A.

-   Then it\'d have to evaluate the lambda, to figure out that it
    > returns a Str.

-   It would line up the return values, to see that Str corresponds to
    > #R.

-   It would see that there was no conflict.

-   It would also try any other \_\_call that were in scope, but reject
    > them since their arguments didn\'t match. (If any more did match,
    > we\'d throw an error or choose the best one?)

It takes the knowledge that #A = Int, #R = Str, and that our \_\_call is
main:lam1, and proceed with the original sweep of evaluation.

## Complex

Imagine we had multiple things in the interface:

> interface ResultOr\<Ref#N, Ref#M, Ref#Z\> {
>
> fn moo(this, n: #N, z: #Z \> Mork) Void;
>
> fn boo(this, m: #M, z: #Z \< Bork) Void;
>
> }

In the \"searching\" phase we\'d have to combine *all* these rules, and
try both of these functions at the same time, so that we can accurately
figure out what #Z is.

## Temporary Solution

For now, we\'ll say that any incoming function cannot have an
auto-inferred return type, it must be known some other way. That means
we\'ll have to explicitly say the return type Array(5, {()Str \"elem\" +
\_}) or manually specify the templata like Array\<Str\>(5, {\"elem\" +
\_}).

\_\_Array is also difficult because it requires a IFunction\<\_, #A,
#R\>; it doesn\'t know how to infer that \_ and it needs that to get the
actual InterfaceRef2.
