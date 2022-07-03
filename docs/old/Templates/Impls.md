# Need Match-Only Rule For Impls

(NMORFI)

Also: Matching Doesnt Evaluate Struct Or Interface (MDESOI)

We kept hitting stack overflows when we had a *lot* of impls in our
program. This is because:

1.  Every time we evaluated a struct, we also figure out all of its
    > parent interfaces, which

2.  required that we evaluate all of the impls, to see if we\'re the
    > interface, which meant

3.  evaluating all of the rules in the impl, which meant

4.  evaluating the struct part of the impl.

This all meant that whenever we evaluated any struct, we evaluated all
structs involved in any impl anywhere in the program.

To avoid this, we needed to change rule 3 and avoid rule 4.

> impl\<T\> MyInterface\<T\> for MyStruct\<T\> where T \< ISomething;
>
> impl ISpaceship for Serenity;

We need to match against the MyInterface\<T\> or ISpaceship and **not**
evaluate it. We need to make it so Matching Doesnt Evaluate Struct Or
Interface (MDESOI).

This problem seems to be unique to impls because they all have the same
name, so we can\'t narrow it down in any way beforehand.

**Solution A: Special \"match\" Rule** (doesnt work)

Let\'s say we have a special rule, \"match\", for which information can
only flow downward into the rule. For example, for:

> impl\<T\> MyInterface\<T\> for MyStruct\<T\> where T \< ISomething;

whose rules would have been:

1.  I = MyInterface\<T\>

2.  S = MyStruct\<T\>

3.  (T \< ISomething) = true

we would instead make the:

1.  I = match(MyInterface\<T\>)

2.  S = match(MyStruct\<T\>)

3.  (T \< ISomething) = true

Now, when we come in with a known S such as MyStruct\<int\>:

1.  We\'ll send that downwards into the rules, and rule 2 will figure
    > out that T is an int.

2.  We\'ll reach a deadlock because we can\'t evaluate MyStruct\<T\>.

Darn.

**Solution B: Ordering** (doesnt work)

Our rule engine works by evaluating each rule, and repeating until there
are no unknown runes left. This doesn\'t quite work because:

-   If the struct rule is first, then when we\'re trying to match a
    > given interface against an impl, we\'ll be evaluating Serenity in
    > the above example.

-   If the interface rule is first, then when we\'re trying to match a
    > given struct against an impl, we\'ll be evaluating ISpaceship in
    > the above example.

So we can\'t solve this with just ordering.

**Solution C: Special \"match\" Rule, Depending on Direction**

This adjusts solution A to work.

If we\'re starting from a struct, we\'ll have a match on the S rule,
like this:

1.  I = MyInterface\<T\>

2.  S = match(MyStruct\<T\>)

3.  (T \< ISomething) = true

and if we\'re starting from an interface, we\'ll have a match on the I
rule, like this:

1.  I = match(MyInterface\<T\>)

2.  S = MyStruct\<T\>

3.  (T \< ISomething) = true

This means we\'ll need to have two sets of rules for every impl, which
is fine. Only slightly annoying.

(We could even have an eval rule someday that does the opposite)

**Solution D: Ordering, Depending on Direction**

This adjusts solution B to work.

If we\'re starting from a struct, we can put the struct rule first.

If we\'re starting from an interface, we can put the interface rule
first.

That way, it will fail early, before it evaluates the other rule.

D looked nice since it doesn\'t require a new rule. However, it would
mean we can\'t arbitrarily reorder the rules, or solve things in an
arbitrary order. We should probably avoid that kind of thing, so that
later when we have better solvers, we don\'t have to constrain them with
orderings.

So, we\'ll go with D for now, but long term we\'ll want to do C. We also
need to make sure MDESOI is true, for both calls (MyInterface\<T\>) and
non-calls (Serenity).

# Giving Argument Ownership For Param Subtypes

(GAOFPS)

In InfererEvaluator (search for GAOFPS) we\'re trying a bunch of
possibilities: the argument and all of its supertypes.

However, we\'re not just feeding in the kind into the rule solver\...
we\'re feeding in the given argument ownership. We actually have to,
because otherwise, we reach an impasse for this function:

> fn something(abstract x IMyInterface\<T\>) int;

when we feed in the argument:

> MyInterface\<int\>

because we send MyInterface\<int\> into the Param1Coord rune, and then
match it downwards into the IMyInterface\<T\> template call, to figure
out the T\... note the first part, \"send MyInterface\<int\> into the
Param1Coord rune\". That means MyInterface\<int\> needs to be a coord.
To do that, it needs an ownership. **The only one we have handy is the
one given in the argument.** So, we let it creep into the rule solver,
and rely on it hitting a conflict later on when we match it downwards
into the IMyInterface\<T\>.

That seems shifty. It feels like the arguments are having a little too
much influence over the rest of the rules.

In a perfect world, we\'d have rules like

> Param1Kind = IMyInterface\<T\>
>
> Param1Ownership = own (figured out from IMyInterface template)

and from argument:

> Param1Kind = IMyInterface\<int\>

To solve this, we need to do these things:

-   Make it so the astronomer automatically inserts a toRef call when it
    > sees us trying to use a kind (such as IMyInterface\<T\>) as a
    > parameter.

-   Make it so we can produce partial information, toRef might not know
    > what a IMyInterface\<T\> is, but it can know that the resulting
    > coord has ownership Own or Share.

-   Make it so that partial information can flow backwards into the
    > incoming IMyInterface\<int\>, so that we can pair it with the
    > own/share, produce a full coord, and the send it into matching
    > against the IMyInterface\<T\>.

Or, instead of this kind of partial information, perhaps it would be
better to do some transitivity somehow. Given the rules

> Param1Coord = toRef(IMyInterface\<T\>)
>
> Param1Coord = Ref(anonRune1, IMyInterface\<int\>)

we might lower that to:

> Param1CoordOwnership = anonRune2 = anonRune1
>
> Param1CoordKind = IMyInterface\<T\> = IMyInterface\<int\>

and learn that T = int, in which case toRef(IMyInterface\<T\>) will
actually solve.

Perhaps Astronomer can do this, it can plug dummy values in for every
coord component and every kind component, and then in the end, equate
them all together. (Also taking into account unidirectionality like from
NMORFI)

# Should Change Coercing to toRef

(SCCTT)

Right now we\'re doing coercing to make kinds into coords. Instead, we
should just have astronomer insert toRef calls where it sees us trying
to access a kind as a coord.

This will resolve a couple hacks, search for SCCTT.

# Impl Name is Subcitizen Human Name

(INSHN)

The subcitizen of an impl is the substruct/subinterface.

For example:

-   In impl IUnit for Marine, the impl\'s name is Marine.

-   In impl ICollection\<T\> for List\<T\>, the impl\'s name is List.

This is an optimization to make it faster to find all parent interfaces
of a certain thing.

It unfortunately means we can\'t do something like:

> impl ICollection\<T\> for Y where Y \< MyThing\<T\>;

but that\'s probably super rare, and the optimization is worth it.

Anonymous substructs are the interface name plus
AnonymousSubstructName(), so they use the name of the interface they\'re
implementing. This is a little awkward because it\'s the opposite of how
the name is normally the subcitizen and here it\'s the interface, but
that should be fine.
