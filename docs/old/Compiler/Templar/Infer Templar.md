> [[Predictor]{.underline}](#predictor)
>
> [[How it Works]{.underline}](#how-it-works)
>
> [[Evaluate]{.underline}](#evaluate)
>
> [[Equals]{.underline}](#equals)

[[Unknown Needs
DeeplySatisfied]{.underline}](#unknown-needs-deeplysatisfied)

[[Double Constraint Refs
Contradiction]{.underline}](#double-constraint-refs-contradiction)

[[Send and Impl Rules For Upcasts
(SAIRFU)]{.underline}](#send-and-impl-rules-for-upcasts-sairfu)

[[Some Rules Can Add More Puzzles
(SRCAMP)]{.underline}](#some-rules-can-add-more-puzzles-srcamp)

[[Whether To Merge Equal Rules Beforehand
(WTMERB)]{.underline}](#whether-to-merge-equal-rules-beforehand-wtmerb)

[[How To Know Somethings\' Template
(HTKST)]{.underline}](#how-to-know-somethings-template-htkst)

[[Solver Must Choose Most Specific Type
(SMCMST)]{.underline}](#solver-must-choose-most-specific-type-smcmst)

> [[Example 1]{.underline}](#example-1)
>
> [[Example 2]{.underline}](#example-2)
>
> [[Solutions]{.underline}](#solutions)

[[Complex Solve As Last Resort
(CSALR)]{.underline}](#complex-solve-as-last-resort-csalr)

[[Should We Solve Kinds
First]{.underline}](#should-we-solve-kinds-first)

[[Subtypes in Solver]{.underline}](#subtypes-in-solver)

There\'s three stages to templating.

## Predictor

> fn add(a: #T, b: #T) #T
>
> where(#T = Int)
>
> { \... }

Predictor will look at a set of rules and figure out if we can infer all
of them right now, just by looking at only that function.

For this function, predictor will return true, meaning we know all the
types, meaning it\'s not a template.

> fn add(a: #T, b: #T) #T
>
> where(#T = Int)
>
> { \... }

because we can figure out that T is an Int.

For this function, predictor returns false, meaning we can\'t figure out
all the types up front, meaning it\'s a template.

> fn add(a: #T, b: #T) #T { \... }

## How it Works

The predictor works by repetitively propagating information across the
rules. To understand how, one must understand the \"evaluate\" step.

### Evaluate

The \"evaluate\" step, given what runes we already \"know\", will look
at a rule and figure out its Result which contains the **isKnown**
boolean, the **unknownRunes** set, and the **newlySolvedRunes** set
(ignore the newlySolvedRunes set for now, that\'s explained later).

Let\'s say we already know coord CK and kind KK. We don\'t know coord CU
and kind KU.

Here\'s some examples of when we can get various results:

  --------------------------------------------------------------------------------
  **Example**                                            **isKnown**   **Unknown
                                                                       Runes**
  ---------------------------------------------- ------- ------------- -----------
  Ref\[#KU, \_, \_, \_\]                                 false         KU

  Ref\[\_, \_, \_, \_\]                                  false         (none)

  ownership(#CU) = borrow                                true          CU

  List:#CK                                               true          (none)
  --------------------------------------------------------------------------------

Here are the rules for calculating isKnown:

-   A Ref components rule is known if we all the components are known.

    -   Ref\[own, inl, rw, Marine\] is **known**.

    -   Ref\[own, inl, rw, #KU\] is **unknown**.

-   A Kind components rule is always unknown. Kind\[mut\] isn\'t enough
    > info to know the kind.

-   A call templex is known if we know the template and the args.

    -   List:#CK is **known**.

    -   List:#CU is **unknown**.

-   A call rule is known if we know the arguments.

    -   ownership(#CU) is **unknown**.

    -   ownership(#CK) is **unknown**.

-   An = rule is known if we know either side.

    -   #CU = Marine is **known**.

    -   #CK = #CU is **known**.

    -   Ref\[\_, \_, \_, \_\] = #CU is **unknown**.

As we evaluate something,

### Equals

The = rule is how we figure out what the unknown runes are.

What follows is a simplification, as if there was no **match**ing in the
infer templar.

Let\'s consider the rule =(#CK, Ref\[\_, \_, \_, #KU\]). The result of
this rule is **known** but that\'s not very helpful\... we want to know
what #KU is! Looking at this, we should be able to figure out #KU, since
we know what #CK is. Here\'s how.

When we **evaluate** an = rule, if only one of the sides is known, we
try and propagate the \"known-ness\" to the other side. In this example,
we propagate the known

The predictor will

\"evaluate\" various parts of the rules.

ask each part of the tree about its situation, represented by

Predictor repetitively \"broadcasts\" all of the known runes downwards
into the tree of rules.

When we broadcast downwards, we

Let\'s say we start with these rules. Unknown parts are gray.

> Ref\[ownership(#CU) = borrow, inl, const, #KU = #KC\]

There are parts that are always known (things from NameST, and constants
like OwnP) so we\'ll show them as green.

Ref\[ownership(#CU) = borrow, inl, const, #KU = #KC\]

> List(
>
> ComponentsSR(
>
> TypedSR(Some(\"T\"),CoordTypeSR),
>
> List(
>
> Some(OrSR(List(
>
> TemplexSR(OwnershipST(OwnP)),
>
> TemplexSR(OwnershipST(ShareP))))),
>
> None,
>
> None,
>
> Some(OrSR(List(
>
> TypedSR(None,StructTypeSR),
>
> TypedSR(None,ArrayTypeSR),
>
> TypedSR(None,SequenceTypeSR)))))),
>
> EqualsSR(TypedSR(Some(\"T\"),CoordTypeSR),TemplexSR(NameST(\"Marine\")))))

When we broadcast downwards, we

  ---------------------------------------------------------------------------------
  **Example**                                   **isKnown**   **Unknown   **Newly
                                                              Runes**     Solved
                                                                          Runes**
  --------------------------------------------- ------------- ----------- ---------
  Ref\[\_, \_, \_, #KU\]                        false         KU          (none)

  =(#CU, Ref\[\_, \_, \_, =(#KU, #KC)\])        false         CU          KU

  Ref\[\_, \_, \_, \_\]                         false         (none)      (none)

  Ref\[\_, \_, \_, =(#KU, #KC)\]                false         (none)      KU

  =(ownership(#CU), borrow)                     true          CU          (none)

  Ref\[=(ownership(#CU), own), inl, rw, =(#KU,  true          CU          KU
  #KC)\]                                                                  

  List:#CK                                      true          (none)      (none)

  =(#CK, #CU)                                   true          (none)      CU
  ---------------------------------------------------------------------------------

Matching Doesnt Match Into Arguments

MDMIA

Take these example rules:

> =(mutability(Ref#CU\[\_, \_, \_, #KU\]), mut)
>
> =(#CK, #CU)

When we evaluate that first rule:

-   In evaluating the = we see that we don\'t know the left side, but we
    > do know the right side.

-   Since the = knows the right side and not the left, it will try and
    > match the left side against what it got from the right (the mut).

-   It tries to match the mut against the mutability(\...) call.

-   It can see that yep, mutability(\...) returns mutabilities, and mut
    > is a mutability\...

-   \...but what do we do with the argument, the Ref#CU\[\_, \_, \_,
    > #KU\]? We don\'t know what coord to feed downwards!

and so the matching stops right there.

That would initially seem to be a problem. However, we then evaluate the
second rule and see that #CU equals #CK. Now we have more information.

When we try to evaluate the =, we suddenly find that we have enough to
evaluate the entire thing!

Moral of the story: the matching phase can\'t match down through
function calls\... but we don\'t really need to.

If we do one day discover that we need to, we *could* change our
matching phase to try to evaluate each argument. I can\'t think of a
case that needs it yet.

toRef(#C) is a temporary thing, a shortcut for Ref\[own, yon, rw, #C\].
later we might obviate it with coercing.

we can\'t flatten these completely into a flat list of constraints
(connected by runes) because of the Or rule. The Or has to contain its
own little universe. So it would be kind of mostly flat\... but Ors
would still have their subtrees.

unless\... we put the ors into their own little subfunction. hmm\...

# Unknown Needs DeeplySatisfied

IEUNDS

(also ARCDS: Anonymous Runes Considered Deeply Satisfied)

Let\'s say we have:

> Ref\[\_, \_, \_, #K like IMoo:#T\]

It\'s weird alone, but it\'s perfectly valid.

Let\'s also say we don\'t know #T yet, but we do know #K. In this case,
the result is unknown, and we haven\'t deeply satisfied things.

Once we do know #T, it will then report deeply satsfied.

# Double Constraint Refs Contradiction

(DCRC)

Say we have these:

> struct MyBox\<X\> { x X; }
>
> fn myFunc\<T\>(box &MyBox\<T\>) &T { \... }

There\'s an implicit R in there, theres a rule that says:

> R = &T

However, there\'s a problem when we call this function with a
MyBox\<&Ship\>. It:

-   Concludes that T = &Ship, from evaluating the parameter rule.

-   Tries evaluating R, finds that it\'s unknown.

-   Tries evaluating &T, which is &&Ship, which simplifies to &Ship.

-   Sets R to be &Ship.

-   In a later pass, it tries evaluating R = &T.

-   In that, it evaluates R, gets &Ship.

-   It sends that to the right side of the R = &T rule, like &Ship -\>
    > &T.

-   Concludes that T is Ship, **which contradicts.**

**Option A: Hack it**

Flip the rule so it\'s &T = R.

That way, in the later passes, when we already know T and R, it will
evaluate &T to &&Ship to &Ship, and agree with R.

**Option B: Unidirectional Flow**

Option A shows us that the direction matters. Maybe we can make sure
that we always evaluate in a certain direction.

**Option C: Allow Double References**

If we allow &&Ship inside the generics engine, then things seem to work.
It would conclude that R is &&Ship, and things would work. At the last
second, we could reduce all the &&Thing to &Thing.

**We\'ll go with A for now,** and probably C long term.

Open questions:

-   Do we even have this problem anymore with the normalized solver?
    > Once we solve a rule, it\'s never executed again. We probably do?

-   Can we have opt-in unidirectional flow? Some sort of templex that
    > establishes a world that can receive but not send type
    > conclusions?

# Send and Impl Rules For Upcasts (SAIRFU)

Previously: Impl Rule For Upcasts (IRFU)

a MyInterface = MyStruct(1337);

Let\'s say a\'s coord rune is X. We can\'t say:

-   X = MyInterface

-   X = MyStruct

because that\'s a conflict.

We could just not feed the X = MyStruct into the solver, but then this
breaks:

> a = MyStruct(1337);

because it can\'t figure out what X should be.

We also can\'t know ahead of time (before running the solver) if the
receiver is an interface or a struct, so we can\'t add rules depending
on that.

So we\'ve established that both pieces of information (sending type and
receiving type) need to be present from the start, and we can\'t relate
them with an equals.

So, we have to add an interesting rule that:

-   When the receiver is a struct, we can know the sender must also be
    > that struct. It\'s like we\'re \"short-circuiting\" this to act
    > like an equals. (Later, perhaps we\'ll have a way to force
    > downcast)

-   Otherwise, the receiver is an interface, we wait until we know both
    > sides, and then check that one is indeed a subclass of the other.

Note how sometimes the rule can \"short circuit\". This means the rule
doesn\'t truly know its puzzles until it determines one of its runes.

We\'ll use rule replacing to do this. See SRCAMP for more on this.

We\'ll call this new rule a \"Send\" rule. It will short-circuit if
possible, otherwise replace itself with a more precise rule (an Impl)
depending on what it finds.

-   If sending from non-descendant (primitive, array, impl-less struct),
    > **short-circuit; conclude they\'re equal**.

-   If sending into non-ancestor (primitive, array, struct),
    > **short-circuit; conclude they\'re equal.**

-   If sending from descendant (struct with impl), we need to wait until
    > we know the receiver, at which point we know both, so replace with
    > an Impl.

-   If sending into ancestor (an interface), we need to wait until we
    > know the sender, at which point we know both, so replace with an
    > Impl.

Note that an interesting case arises when we\'re receiving into a
generic: we have no idea whether it\'s an ancestor or non-ancestor!

In that case, the call-site will need to guess, and choose the most
specific type, see SMCMST.

# Some Rules Can Add More Puzzles (SRCAMP)

Like explained in Impl Rule For Upcasts (IRFU), the impl rule doesn\'t
really know if just its receiver rune is enough.

**Approach A: Add Puzzle (or Rule)**

So, the impl rule will have one puzzle, with only the receiver rune.
When that puzzle is solved, we can either solve the rune, or add a new
puzzle. Or rather, we can **add a new rule which has a new puzzle**,
since that will fit with the current system which resolves the entire
rule when any of its puzzles are done.

**Approach B: Conditionals**

what if we conditionally solve certain runes? like, a puzzle might solve
one rune or the other depending on whatever.

ImplOrEquals is like \"A receives B\". It runs when we have B, and
solves either X or Y.

then, we can have two other rules:

-   IfThenEquals, \"if X, A = B\" runs when we have X and A, or X and B.

-   IfThenImpl, \"if Y, A impls B\" runs when we have Y, A, and B.

it does mean some rules will never run though.

this seems like it will needlessly add rules to the state that we\'ll
never run.

**We\'ll go with A.**

Also, both of these kind of mean we won\'t be planning any rule
execution order beforehand. T_T

# Whether To Merge Equal Rules Beforehand (WTMERB)

Let\'s say we have these two rules:

X = int

Y = X

When we \"canonicalize\" these runes, to turn them into integers for
speed, we could just assign them the same rune, since we know they\'re
equal.

However, let\'s not do that, because:

-   It won\'t actually save us much time overall, whether we deduplicate
    > ahead of time or just propagate it through in the solver itself.

-   We sometimes add Equals rules late, during solving itself, see
    > SRCAMP.

-   We might have some Equals rules that conditionally run, if they\'re
    > part of Or rules, and those can\'t be merged ahead of time.

# How To Know Somethings\' Template (HTKST)

Let\'s say we have this function:

> fn add\<T\>(self &List\<T\>, element T) { \... }

and we want to put it into the overload index, knowing that its first
parameter is \"some kind of List,\" in other words, its template is
List.

How do we determine things\' templates?

We\'ll run this through a \"template solver\", basically one that
doesn\'t need template arguments for calls. It\'ll see that we\'re
calling the \"List\" template with \"whatever\" arguments, and just
conclude that it\'s template is List.

# Solver Must Choose Most Specific Type (SMCMST)

#### Example 1

Let\'s say we have:

> fn launch\<T impl IShip, Y impl IWeapon\>(ship &T, weapon &Y) &T {
> \... }

and we want to call it with:

> myFirefly = Firefly();
>
> myWeapon = Rocket();
>
> a = launch(&myFirefly, &myWeapon);

At the call site, what\'s the actual type of A? There\'s actually two
valid solutions:

-   a\'s type is Firefly.

-   a\'s type is IShip.

The most useful answer would be Firefly, but how would the solver know
to choose that?

#### Example 2

Let\'s say we have:

> fn launch\<T impl IShip\>(a &T, b &T) &T { \... }

Let\'s say we pass in two kinds of ships:

> a = launch(&myFirefly, &mySerenity);

here we need to choose the common ancestor between Firefly and Serenity,
which is IShip.

#### Solutions

We need some way for the solver to say \"choose the most specific
type\". But when do we do that?

Approach A: Keep track of a running \"possibilities\" set for each rune.
Most runes will have only one possibility. In this, we have a bunch of
possibilities:

-   T can be Firefly or IShip

-   Y can be Rocket or IWeapon

we would probably have a rule beforehand which establishes these
possibilities, perhaps the Sends or Isa rule would make these happen
when they get their sender/subtype.

Approach B: Wait for the solver to halt, then go through any unsolved
Sends or Isa rules which know their senders, and choose the most
specific one.

In both, we need to at some point take a leap and assume the narrowest
possible, so we\'ll go with approach B which makes that a little simpler
and more explicit.

# Complex Solve As Last Resort (CSALR)

To accomplish SMCMST, the solver will ask its delegate to do a \"complex
solve\", as a last resort if it can\'t figure out everything the normal
way.

The previous, normal way to solve is a \"simple\" solve.

Hopefully, we can find a way to optimize these and maybe even fold them
back into the main simple solver. Currently, they do some looping, which
causes a O(n\^2).



# Should We Solve Kinds First

This snippet:

> fn launch\<T\>(ship &ISpaceship\<T\>) { }
>
> a = launch(&Firefly\<int\>());

has these rules:

> fn launch\<T\>(ship &ISpaceship\<T\>) { }
>
> \^\^\^\^\^\^\^\^\^\^\^\^\^ \_1
>
> \^\^\^\^\^\^\^\^\^\^ \_2
>
> \_2 = ISpaceship
>
> \_3 = void
>
> \_0 = &\_1
>
> \_1 = \_2\<T\>
>
> (arg 0) -\> \_0

and in solving, we have these conclusions:

> \_3: void
>
> (arg 0): &Firefly\<i32\>
>
> \_2: ISpaceship

that last rule becomes:

> (arg 0) isa \_0

and then we basically have this situation:

> (arg 0) -\> \_0, \_0 = &\_1, \_1 = \_2\<T\>

This is particularly difficult for the solver to identify.

Right now it:

-   Figures out what all the \"kind equivalent\" runes are: which runes
    > are guaranteed to have the same kind. Here, we conclude that \_0
    > and \_1 will definitely have the same kinds.

-   Sees if there are any runes for which there are at least one send
    > (-\>) rule.

-   If there are also some call rules for it (like above), then figure
    > out which ancestor of the sender satisfies the call.

-   If there are no call rules for it, assume the most specific kind
    > (Firefly\<i32\> here)

**Approach A**

Run all this logic before we even consider a coord vs kind difference.
Basically ignore Augment and CoordComponents rules.

But, this kind of cuts off the Prot rule. Maybe we should cut it off
anyway? Or rather, it means we cant use it until the very end.

**Approach B**

Establish more precise relationships between coords.

-   \_0 = &\_1 becomes:

    -   \_0_kind = \_1_kind

    -   \_0_ownership = constraint

    -   \_0_permission = readonly

    -   \_0 = Ref(\_0_kind, \_0_ownership, \_0_permission)

-   \_1 = \_2\<T\> becomes:

    -   \_1_kind = \_2\<T\>

    -   \_1_ownership = defaultOwnership(\_1_kind)

    -   \_1_permission = defaultPermission(\_1_kind)

    -   \_1 = Ref(\_1_kind, \_1_ownership, \_1_permission)

-   (arg 0) -\> \_0 becomes:

    -   (arg 0 kind) -\> \_0_kind

    -   (arg 0 ownership) = \_0_ownership

    -   (arg 0 permission) = \_0_permission

    -   (arg 0) = Ref( (arg 0 kind), (arg 0 ownership), (arg 0
        > permission) )

Now it\'s a little easier, we don\'t have to deal with the Augment rule
(\_0 = &\_1)

**Comparison**

Either way, we have to do union finds to deal with equals rules, we have
to crawl across equalses.

# Subtypes in Solver

Let\'s say we have:

> fn launch\<T impl IShip\>(a &T, b &T) &T { \... }

Let\'s say we pass in two kinds of ships:

> a = launch(&myFirefly, &mySerenity);

We know that:

-   arg_0\_kind -\> T_kind

-   arg_1\_kind -\> T_kind

They\'re both lower bounds.

We know that we need both of those to actually solve T_kind.

So why don\'t we make a puzzle out of it? We can merge those two rules
into:

T_kind = mostSpecificCommonAncestor(\[arg_0\_kind, arg_1\_kind\])

Also, when we do:

> struct MyBox\<X\> { x X; }
>
> fn compare\<T\>(box &MyBox\<T\>, that &T) { \... }

arg_0 = &MyBox\<&Ship\>

arg_1 = &Ship

arg_0 = param_0

arg_1 = param_1

param_0 = &\_X

\_X = MyBox\<T\>

param_1 = &T

arg_0\_kind = param_0\_kind

arg_0\_ownership = param_0\_ownership

arg_1\_kind = param_1\_kind

arg_1\_ownership = param_1\_ownership

param_0\_kind = \_X_kind

param_0\_ownership = borrow

\_X_kind = MyBox\<T\>

\_X_ownership = ???

param_1\_kind = T_kind

param_1\_ownership = borrow

simplified:

> arg_0\_ownership = borrow
>
> arg_1\_ownership = borrow
>
> arg_0\_kind = MyBox\<T\>
>
> \_X_ownership = ???

arg_0\_kind = MyBox\<&Ship\>

arg_0\_ownership = borrow

arg_1\_kind = Ship

arg_1\_ownership = borrow

> &Ship = T
>
> \_X_ownership = ???

arg_0\_kind = MyBox\<Ship\>

arg_0\_ownership = borrow

arg_1\_kind = Ship

arg_1\_ownership = borrow

> arg_0\_ownership = borrow
>
> arg_1\_ownership = borrow
>
> MyBox\<Ship\> = MyBox\<T\>
>
> Ship = T
>
> \_X_ownership = ???

Also, when we do:

> fn compare\<T\>(box T, that &T) { \... }

arg_0\_kind = param_0\_kind

arg_0\_ownership = param_0\_ownership

arg_1\_kind = param_1\_kind

arg_1\_ownership = param_1\_ownership

param_0\_kind = T_kind

param_0\_ownership = T_ownership

param_1\_kind = T_kind

param_1\_ownership = borrow

arg_0\_kind = Ship

arg_0\_ownership = borrow

arg_1\_kind = Ship

arg_1\_ownership = borrow

> Ship = param_0\_kind
>
> borrow = param_0\_ownership
>
> Ship = param_1\_kind
>
> borrow = param_1\_ownership
>
> Ship = T_kind
>
> borrow = T_ownership
>
> Ship = T_kind
>
> borrow = borrow

arg_0\_kind = Ship

arg_0\_ownership = own

arg_1\_kind = Ship

arg_1\_ownership = borrow

> Ship = param_0\_kind
>
> own = param_0\_ownership
>
> Ship = param_1\_kind
>
> borrow = param_1\_ownership
>
> Ship = T_kind
>
> own = T_ownership
>
> Ship = T_kind
>
> borrow = borrow

Also, when we do:

> fn compare\<T\>(that &T) T { \... }

arg_0\_kind = param_0\_kind

param_0\_kind = T_kind

param_0\_ownership = borrow

T_ownership = ???

arg_0\_kind = Ship

arg_0\_ownership = own

> Ship = param_0\_kind
>
> Ship = T_kind
>
> param_0\_ownership = borrow
>
> T_ownership = ???

\...but what\'s T_ownership?

it seems nobody cares.

either would satisfy.

how about T = own?

how about T = borrow?

lets assume T = own, because its easy for the user to change it to
borrow.

its interesting that we have guessers.

itd likely be easy to have sets, since everythings an index. as we pull
out of it just check if we\'ve already seen that index. ez.

could we do a linear sort? a recursive one, where we do a cuckoo-like
shifting around of things. i daresay so\... but then iterating over them
will hit empty spots.
