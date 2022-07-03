This doc is for the finer points and corner cases of the design, and
some notes on implementation.

[[Runes]{.underline}](#runes)

> [[Why Kind#K instead of #K:
> Kind?]{.underline}](#why-kindk-instead-of-k-kind)

[[Rules]{.underline}](#rules)

[[Patterns]{.underline}](#patterns)

> [[Curly Braces, Square Braces, or
> Parentheses?]{.underline}](#curly-braces-square-braces-or-parentheses)
>
> [[The Case for Parentheses]{.underline}](#the-case-for-parentheses)
>
> [[The Case for Curlybois]{.underline}](#the-case-for-curlybois)
>
> [[Do Runes Capture Kinds or Coordinates?
> (RCKC)]{.underline}](#do-runes-capture-kinds-or-coordinates-rckc)
>
> [[Atoms]{.underline}](#atoms)
>
> [[Must Explicitly Destructure
> Packs]{.underline}](#must-explicitly-destructure-packs)
>
> [[Requiring Runes for
> Templates]{.underline}](#requiring-runes-for-templates)
>
> [[Virtual/Override]{.underline}](#virtualoverride)
>
> [[Capturing Kinds and
> Ownerships]{.underline}](#capturing-kinds-and-ownerships)
>
> [[Possible Values Shouldnt Be Used For
> Inference]{.underline}](#possible-values-shouldnt-be-used-for-inference)
>
> [[Can\'t Have Rule Components Without Rule
> Type]{.underline}](#cant-have-rule-components-without-rule-type)
>
> [[Ambiguities]{.underline}](#ambiguities)
>
> [[Rune With Kind Is Like
> Call]{.underline}](#rune-with-kind-is-like-call)
>
> [[Dot Gets Components Or
> Fields]{.underline}](#dot-gets-components-or-fields)

[[Implementation]{.underline}](#implementation)

> [[Neighboring]{.underline}](#neighboring)

[[Alternative: Without
Hashes]{.underline}](#alternative-nohash-approach)

> [[Comparing Functions]{.underline}](#comparing-functions)
>
> [[Comparing Lets]{.underline}](#comparing-lets)
>
> [[Comparing Impls]{.underline}](#comparing-impls)
>
> [[Comparing Templated
> Closures]{.underline}](#comparing-templated-closures)

[[Uncategorized Notes]{.underline}](#uncategorized-notes)

See
[[https://docs.google.com/document/d/1ot3SFQ1GZyP5IlhffqRFC\_-3jiv7HLURKFaenq54Zp0/edit#]{.underline}](https://docs.google.com/document/d/1ot3SFQ1GZyP5IlhffqRFC_-3jiv7HLURKFaenq54Zp0/edit#)
for requirements

Goal: want to be able to use the parser\'s output for the formatter.
Because of that, the parser can\'t do things like turning fn(Int)Bool
into IFunction:(mut, Int, Bool).

\"rules\" refers to the where clause.

# Runes

Imagine this example:

> fn sum(a: #T, b: #T) #T = a + b;

That #T is not known right away, it\'s different for each invocation.
It\'s not a specific type like MyIntList where everything is known about
it, it\'s a placeholder. It\'s a parameter.

(It was called rune from \"**ru**le **n**am**e**\", since they mostly
appeared in rules. They appear in patterns and return types now, but the
name was cool so it stuck.)

The #T We know that the #T in a: #T is a rune because it\'s specified in
the \"identifying runes\" list, right after the sum:. A rune can also be
introduced in the rules:

> fn sum(a: X) Void
>
> where(let X = MyList:Int)
>
> = a + b;

X is a rune because it\'s in a let clause in the rules.

Note how we can actually figure out, without a call, that X is a
MyList:Int. That\'s fine; a rune doesn\'t have to be unknown.

## Why Kind#K instead of #K: Kind?

In general, it\'s because we\'ll often want to combine these two rules:

> #R: Ref
>
> #R.kind = #K

like so:

> (#R: Ref).kind = #K

and it\'s much easier when the \"Kind\" is on the left side, so the #K
can connect the two:

> Ref#R.kind = #K

A bigger example, let\'s take this code:

> cfn flattenAll(#G: Ref) {
>
> #T: cfn(Ref)Kind;
>
> #R: Ref;
>
> #T:#R = #G.kind;
>
> implements(#G.kind, ICollection:#R
>
> #E: Ref;
>
> implements(#R.kind, ICollection:#E
>
> }
>
> fn(thing: #G): #T:#EE {
>
> thing.flatten
>
> }

We pulled the declarations of #T, #R, and #E out on their own line
because if we had them inline, it would look like:

> cfn flattenAll(#G: Ref) {
>
> (#T: cfn(Ref)Kind):(#R: Ref) = #G.kind;
>
> implements(#G.kind, ICollection:#R)
>
> implements(#R.kind, ICollection:(#E: Ref))
>
> }

It\'s very noisy. Instead, let\'s use (type)#(rune):

> cfn flattenAll(Ref#G) {
>
> cfn(Ref)Kind#T:Ref#R = #G.kind;
>
> implements(#G.kind, ICollection:#R)
>
> implements(#R.kind, ICollection:Ref#E)
>
> }

Still pretty noisy, but a lot better. And it\'ll be even better with
syntax highlighting.

**Note:** It\'s still not that clear that we want to choose one over the
other. We could offer both. Or we could experiment; it\'s only a
parser-level difference.

# Rules

Rules are specified after the parameters and return type:

> fn sum(a: #X) Void
>
> where(#X = MyList:Int)
>
> = a + b;

We put it after because the parameters are more important to people
looking at the function.

The rules serve two purposes:

-   Put constraints on what the runes can be, and

-   Define some runes for use in the function body.

both of these concerns come after the parameters.

# Patterns

## Curly Braces, Square Braces, or Parentheses?

To destructure, we could do square braces:

> fn moo(a: Marine\[hp, str\])

or curly braces, like:

> fn moo(a: Marine{hp, str})

or parentheses:

> fn moo(a: Marine(hp, str))

Keep in mind, we want to support custom extractors. I want to be able to
call a function from inside a pattern:

> match myMarine {
>
> :Marine\[hp, isEven(\_)\] = 10;
>
> }

and we want to support a toSeq method to override the default exploding:

> struct Marine {
>
> hp: Int;
>
> mp: Int;
>
> priv cachedDamage: Int;
>
> }
>
> fn toSeq(this: &Marine) = \[this.hp, this.mp\];
>
> match u {
>
> :Marine\[hp, mp\] = 6;
>
> \_ = 10;
>
> }

### The Case for Parentheses

This would be quite cool:

> let a: Marine(hp, str) = myMarine;
>
> let a (\_, x) = myMarine

In this scheme, we wouldn\'t call it toSeq, we\'d call it explode or
something.

However, it would be ambiguous with extractors. What is let a (\_, x) =
\... doing? Is it:

-   Calling the function a with the incoming value as the first
    > parameter and local variable x as the second parameter?

-   Capturing the incoming value in a and destructuring, putting its
    > second member into a new local variable x?

Perhaps we could use an operator to call functions\... but it\'d be
gross.

Perhaps we can just assume it\'s calling an extractor. If they wanted to
store a variable and destructure it too they could put the type in
there. Gross.

### The Case for Curlybois

\...is not that strong. There\'s no problems with squaries and they look
better.

## Do Runes Capture Kinds or Coordinates? (RCKC)

For a: #T, or for :Marine\[x:#X\], what do those runes capture? Kinds or
coords?

Either way we go, we can still bail out to template rules to express
what we actually want. The question is what will be more common/useful.

If those capture the kinds:

-   We can\'t easily express that we want to accept any coord there.
    > MutList won\'t be able to easily express that it wants its nodes
    > to hold *anything*.

If those capture the coords:

-   It\'s weird to express a: &#T because T is still a coord.

If we capture both, like a: #C &#K:

-   That\'s super weird and confusing. They should bail out to
    > templating rules if they want to do something that complex.

-   It\'s also impossible to only capture the kind of an owning thing:
    > a: #T. Does that capture the coord or the kind?

We should go with the second, and capture the coords. We can make an
exception for a: &T (aka BCTE, Borrow Coord Tame Exception). The
reasons:

-   We really want to be able to express that MutList can hold anything,
    > since a templated linked list is supposed to be very basic.

-   We want to delay as much as possible the distinction between coord
    > and kind. People should already be experts by the time they have
    > to know about that.

## Atoms

Atoms are the fundamental elements of patterns. Some examples:

-   a

-   a: Int

-   \_: Int

-   :Int

-   a: Marine

-   a: Marine\[hp, str\]

Atoms can contain other atoms.

-   a: Marine\[hp, str\], hp and str are both atoms.

-   a: Wraith\[hp, weapon: BurstLaser\]

-   a: T

-   a: T\[a, b, c\]

-   a: Vec:(N, E)

-   a: Vec:(N, E)\[elements\...\]

+--------+------+-----------------------------------------------------+
| :      |      |                                                     |
| Marine |      |                                                     |
+========+======+=====================================================+
| :&     |      |                                                     |
| Marine |      |                                                     |
+--------+------+-----------------------------------------------------+
| :      | \[a, | Destructuring owning struct                         |
| Marine | b,   |                                                     |
|        | c\]  |                                                     |
+--------+------+-----------------------------------------------------+
| :&     | \[a, | Destructuring borrow struct                         |
| Marine | b,   |                                                     |
|        | c\]  |                                                     |
+--------+------+-----------------------------------------------------+
|        | \[a, | Destructuring owning or borrow sequence or struct   |
|        | b,   |                                                     |
|        | c\]  |                                                     |
+--------+------+-----------------------------------------------------+
| :\^#T  |      | Must be passed in as an own.                        |
|        |      |                                                     |
|        |      | Capture struct or seq kind #\_\_0 in owning coord   |
|        |      | #T.                                                 |
+--------+------+-----------------------------------------------------+
| :\^#T  | \[a, | Must be passed in as an own.                        |
|        | b,   |                                                     |
|        | c\]  | Capture struct or seq kind #\_\_0 in owning coord   |
|        |      | #T.                                                 |
+--------+------+-----------------------------------------------------+
| :&#T   |      | Must be passed in as a borrow.                      |
|        |      |                                                     |
|        |      | Capture struct or seq kind #\_\_0 in owning coord   |
|        |      | #T.                                                 |
+--------+------+-----------------------------------------------------+
| :&#T   | \[a, | Must be passed in as a borrow.                      |
|        | b,   |                                                     |
|        | c\]  | Capture struct or seq kind #\_\_0 in owning coord   |
|        |      | #T.                                                 |
+--------+------+-----------------------------------------------------+
| :#T    |      | Capture coord #T                                    |
+--------+------+-----------------------------------------------------+
| :#T    | \[a, | Capture struct or seq kind #\_\_0 in owning or      |
|        | b,   | borrow coord #T.                                    |
|        | c\]  |                                                     |
+--------+------+-----------------------------------------------------+

Why can\'t we have #T Marine\[a, b, c\]? Why can\'t we have that rune #T
there?

Since #T matches the coord, it won\'t be quite clear to people that that
#T can match both owning or borrowing references. On its face, that
looks like an owning reference. So, we either have just #T, or a kind,
not both.

Why does #T capture a coord and not a kind?

because List:R should be able to contain inl, owning, borrow, shared,
any kind of coord. if it just captured a kind we\'d lose all of that.

Why &#T instead of #T &?

#T & \[a, b, c\] implies that we\'re destructuring a sequence, when
really we\'re destructuring a struct.

Doesn\'t &#T imply that we\'re capturing #T as a kind?

Indeed, the reason we don\'t want R to capture as a kind is because
we\'d lose the other dimensions like inline-vs-separate... so, we
instead want to capture a coord. But, if we make R capture a coord,
it\'s a bit weird because &R together is like a borrow of an own coord,
rather than a borrow of a kind. However, it\'s not that weird if you
come at it from C++ or Rust, where there\'s no distinction between coord
and kind, there\'s just a \"type\". With that in mind, & is just a
modifier of a type, to make a new type.

So, yes, it\'s confusing, but not to newbies. Once someone understands
it a bit, they\'ll see it\'s weird, but at that point, they\'ll
hopefully understand rules.

Why is #T \[a, b, c\] capturing both sequences and structs? In other
words, why can\'t I express that I want to rune capture a templated
sequence destructure?

Because it would have looked like #T \[a, b, c\], which would have left
us with no way to rune capture a templated struct destructure.

Why is there no a: &\[a, b, c\]?

The biggest reason we don\'t have it is that it\'s ambiguous: are a, b,
and c types or atoms?

At some level, we don\'t need it; we can destructure from a borrow
reference. If we hand in a borrow reference to \[a, b, c\] it will
destructure it.

However, the intent here might be to require a borrow sequence. To do
that, we can either:

-   Say a: &#T\[a, b, c\].

-   Add an a &\[a, b, c\] to the parser.

We\'re going with the former for now, because I don\'t want them to get
used to doing any type constriction without a :.

#### Must Explicitly Destructure Packs

(MEDP)

### Requiring Runes for Templates

We should require runes for params that end up templated. That\'s
because it\'s not obvious that this is a template:

> fn moo(a, b \[x, y, z\]){ \... }

but both parameters are actually templated. Let\'s require them to be
runes in that case:

> fn moo(a: #A, b: #B\[x, y, z\]){ \... }

or, if they know the types, they\'ll have to add them:

> fn moo(a: Int, b: \[Int, Bool, Str\]\[x, y, z\]){ \... }

or

> fn moo(a: Int, b \[x: Int, y: Bool, z: Str\]){ \... }

(in which case b is a sequence.)

### Virtual/Override

Params are special, in that they can add \"virtual\", \"for XYZ\",
capturing, and destructuring.

### Capturing Kinds and Ownerships

Note that you can\'t easily capture a tame for the kind, because of some
ambiguity.

For example, in this, are we capturing the kind or the ref?

> fn moo:(T, N, E)(a: T Vec:(N, E))

We could have offered specifying a coord template rule inline like

> fn moo(a: Ref#T\[#O, #K\])

but that would have led to oddities like in this case:

> fn moo(a: #T\[#O, #K \[#E1, #E2\]\] \[a: #Y, b: #Z\])

where #E1 and #Y are redundant, and #E2 and #Z are redundant, and
that\'s just weird.

If they really want to capture the kind, then make them put it in a
template rule:

> fn moo(a: #T)
>
> where{
>
> Ref#T = Vec:(#N, #E);
>
> T\[\_, \_, \_, #K\]
>
> }

Same for ownerships.

### Possible Values Shouldnt Be Used For Inference

(PVSBUFI)

We can specify possible values in a rule, like:

> fn moo(thing: #E)
>
> where{
>
> #O = own \| borrow
>
> Ref#E\[#O, \_, \_, \_\],
>
> }
>
> { \... }

which is easy when we\'re dealing with just ownerships and stuff... but
we can also do it with coords:

> fn addToAllElements(listOrSet: #T, toAdd: #E) #T
>
> where{ #T = List:#E \| Set:#E }
>
> {
>
> listOrSet.map({\_ + toAdd})
>
> }

So the question is, can those Es inside the \| capture?

Answer: NO, dear god no, i dont want to even think about what that
means.

So, for now, we\'ll just say that no capturing can go on inside an \|.

But hey, if there\'s only one thing, and no \|, then sure, infer away.

## Can\'t Have Rule Components Without Rule Type

(CHRCWRT)

We can say Kind#K\[#M\] or #R: Ref\[#O, #P, #L, #K\] where the stuff in
the square brackets are the \"components\". The question: do we always
need to say Kind and Ref? Can we simplify R: Ref\[#O, #P, #L,
:Kind\[M\]\] to Ref\[#O, #P, #L, \[#M\]\]?

Answer: nope! Because a subtype of Kind is Callable:

> #K: Callable\[#M, \[#A, #B, #C\], \[#R\]\]

which means something that\'s callable with arg types A, B, C, and has
return type R.

The very most we could do is assume Kind when there\'s no type name.
Probably best to require it though.

## User Must Specify Enough Identifying Runes

(UMSEIR)

Previously: Compiler Cant Add to User\'s Identifying Runes (CCAUIR)

If we have:

> fn moo:(#K, #V)
>
> (a: Map:(#K, #V, #H))
>
> { \... }

Then we have a problem.

Let\'s say that K and V are the identifying runes. That means that
there\'s only one moo:(Int, Str). But if we call it with a Map:(Int,
Str, BlueHasher) and again with a Map(Int, Str, RedHasher) then uh oh,
we have two moo:(Int, Str)s.

Approach A: Throw an error if we see multiple of these.

Approach B1: Decree that the identifying runes list it must contain
everything that we see in the parameters.

Approach B2: Don\'t allow any anonymous runes in the parameters.

Approach C: Make it so K and V are not the only identifying runes. Since
there\'s an H in the patterns, make it so K, V, and H are all
identifying runes.

Approach D: Make it so the user has to put a \... to let the compiler
fill in the remaining runes

> fn moo:(#K, #V, \...)
>
> (a: Map:(#K, #V, #H))
>
> { \... }

so that it can put the H in there.

Approach E: Decree that the incoming parameters cannot directly say what
H is. When the infer templar comes back with K V and H, we throw away
the incoming argument types, throw away H, and infer it just based on K
and V.

A seems like a landmine where we\'ll catch the errors too late.

E sounds extremely complicated and hard to explain. I can\'t even grok
it.

D seems like it\'s too much cost for this rather rare happening.

C seems to automatically do what the user would have to do in B. That\'s
good, but it\'s also a bit magical and it adds some complexity. Plus, it
introduces more monomorphs, which should probably be an opt-in cost.

B1 and B2 are probably the same thing in the end. Will go with approach
B2.

Though, if there\'s a rune we can figure out from the other runes, that
should be fine. We should have the Planner figure out if there are any
that can\'t be figured out from the identifying runes.

(Lambdas Dont Need Explicit Identifying Runes (LDNEIR))

There is one big exception still: lambdas do not need to explicitly
specify their identifying runes. We should figure their types out from
the generics of the callee.

### Pattern Templex Underscores Become Runes

PTUBR

Imagine this function:

> fn moo(a: Map:(#K, #V, \_){) \... }

What are the identifying runes? The answer would seem to be K and V.

However, I could call this with Map:(Int, Str, BlueHasher) and then
separately call it with Map:(Int, Str, RedHasher). Their K and V are the
same, which means they have the same identifying runes, yet theyre
completely different. It\'s a similar problem to CCAUIR, but with
anonymous runes now.

The answer is that we have to promote them to actual runes. Then CCAUIR
can kick in.

### No Identifying Params From Overrides

NIPFO

Imagine we have a templated type doing an override:

> fn fire(unit: Marine:#T for IUnit:#T, at: Location) { \... }

We have to be able to figure out everything from the parameters and not
care about the virtuality. Otherwise we\'d never be able to call it.

So, overrides can\'t supply any identifying params.

### Destructures Can\'t Supply Identifying Runes

DCSIR

Let\'s say we have a destructure:

> fn fire(unit: Marine:#T\[weapon: #W\]) { \... }

That W can\'t be an identifying rune.

The reason is that, given a Marine:#T, we know the weapon type.

It\'s fine to do this to grab the type for use in the body, but it
shouldn\'t be an identifying rune.

## Ambiguities

Couldn\'t do \[#N Int\] because\... is that a repeater seq of N ints? or
is that an \[Int\] with a capture in it? Decided to make the repeater
sequence do \[#N \* Int\] instead; it always has a \*.

### Rune With Kind Is Like Call

(RWKILC)

~~K:Kind might be two things:~~

-   ~~A kind rune.~~

-   ~~a template call, of template K, with one arg, the type \"Kind\".~~

~~So, when there\'s a rune, we can\'t say something like K:Marine. We
have to say K:(Marine). The only remaining ambiguities are K:Int and
K:Bool.~~

We switched to using \< and \> for template calls, so there\'s no more
ambiguity here.

### Dot Gets Components Or Fields

(DGCOF)

E.ElementType would probably mean, if E is a HashMap:(Int, Str), get the
thing inside it called ElementType, which is Str.

E.mutability would mean get the kind\'s mutability.

These two things conflict.

We could use mutability(E) instead.

And, because we have UFCS, that could be E.mutability().

We might even use m(E) for short. And to be consistent, C.ownership
would be o(C), C.permission would be p(C), C.kind would be k(C),
C.location would be l(C).

# Implementation

### Neighboring

Some rules do \"neighboring\", which is where things can appear next to
each other, and at least one is needed.

For example, in let a: Moo\[b, c\] = m; the a and the rest are
neighbors.

In fact, in the rest, the : Moo is required but the \[b, c\] is
optional, so even that\'s a neighbor.

It can lead to unfortunate ambiguities. For example, let a: #R\[b,
c\]\...

-   Is that a captured sequence, like let a: \[b, c\] with the \[b, c\]
    > captured?

-   Or is that a destructure of #R which is a struct?

The opposite of neighbor is \"atomic\". Some rules are atomic so we can
control those ambiguities.

Atomic rules don\'t have any neighboring.

Both RuleParser and PatternParser can produce rules. The pattern parser
is much more limited in the rules it can produce, because of its much
more compact syntax; the explicit syntax of the template rules is much
more flexible.

Template rules from P stage are hierarchical, after S stage are
flattened.

let myExtractor(can capture thing here)\[thing, otherthing, what\] =
\...

most of the time youll see

let myExtractor(\_)\[thing, otherthing, what\] = ...

Make it so all the built in runes are pushed to the end

weirdness: a parameter a: #T will make a coord T, but a: &#T will make a
kind T.

two options:

-   make &#T capture T as a coord. but, then it\'d be impossible to make
    > an owning one. a templated clone() function would be hard

-   make #T capture T as a kind.

kind of like the latter. more flexibility.

a: #T\[a :Int\]

what does this mean:

fn main(\_) { ... }

does that mean main is templated?

it should probably mean that we should just ignore anything that comes
in there. that\'ll be extremely difficult when it can be any size\...
it\'ll have to be a pointer.

we should either implement it or disallow it.

remember, = means that we\'re grabbing a templata from the environment.

so Kind = #T doesnt make much sense.

#T: Kind makes sense.

Kind\[immutable\] = Int doesn\'t make much sense.

if i have:

Moo:Ref

that \"Ref\" could actually be interpreted as the type \"Ref\" from env
OR an int rule like Moo:(Ref#R).

if I have:

#R = Ref

that Ref could actually be interpreted as the type \"Ref\" from env OR a
ref rule like #R = Ref\[\_, \_, \_, \_\].

if i have:

Ref\[\_, \_, \_, Kind\]

that Kind could actually be interpreted as the type \"Kind\" from env OR
a kind rule like Kind\[\_\].

so, the rule we should use: wherever this might be ambiguous, a
Ref/Kind/Int/etc. needs to be followed either by a rune or
\[components\].

In Moo:Ref, that\'s the \"Ref\" from env. To change it, we\'d say
Moo:Ref#\_ or just Moo:#R and have Ref#R somewhere.

In #R = Ref, that\'s the \"Ref\" from env. To change it, we\'d say #R =
Ref\[\_, \_, \_, \_\], or #R = Ref#\_ or Ref#R.

In Ref\[\_, \_, \_, Kind\], that\'s the \"Kind\" from env. To change it,
we\'d say Ref\[\_, \_, \_, Kind\[\_\]\] or Ref\[\_, \_, \_, Kind#\_\].

Ref\[\_, own, \_, \_\]

how do we know that own isnt something from the environment?

#O = own \| borrow \| share

here too

in english this isnt much of a problem, things can be lowercase and wont
conflict with variable names

i suppose other languages are on their own?

the reason we need to disambiguate anything in the parser with symbols
is because we want to do syntax highlighting, and we dont want to look
at the rest of the program to know whether something\'s a type or a
value.

however, we dont really need that to disambiguate own from something
else, because there\'s a fixed set of keywords. own, borrow, share, raw,
xrw, rw, ro, imm, mut.

i think in this case, it\'s fine because:

-   i\'m english, and variable names are always lowercase

-   these are in rules, which most people wont use anyway

if we were to do stuff similar to patterns\...

we\'d see things like :Ref, :Kind

#A:Ref.kind = own

can\'t we figure out what type is expected based on the rune type?

fn moo

where(#R.ownership = own)

(a: #R)

change rules to not contain =

need to support .kind, .mutability, etc.

need to support signatures, like:

exists(fn call(#H, #K)Int)

need to support specifying types like:

struct HashMap:(#K:Ref, #V:Ref, #H:Kind)

because we dont have nice functions to infer from.

need to support handing in packs, so we can make things similar to
callables

a Callable can be a struct if it has a call operator, or a function

perhaps every function has an implicit struct? maybe theres a builtin
cfn that

will give it to us? yes, perfect.

Callable templata has mutability, params pack, returns pack.

cfn(Kind,Ref)struct

fn main(a: #A)

where(

#B:Coord = List:#A,

#C:Coord = #B \| #A \| Int)

first, we go through and figure out the types of A, B, and C. Note that
we don\'t need to necessarily remember/note the types of the anonymous
runes connecting them together.

\_\_Par0, \_\_Par1, etc are the param runes

\_\_Closure is the closure rune

\_\_Ret is the return rune

\_\_Let0, \_\_Let1, etc. are lets\' coord runes.

\_\_par0 is the first argument

\_\_par1 is the second

\_\_par0mem0 is the first argument\'s first destructure

\_\_par0mem0mem0 is the first argument\'s first destructure\'s first
destructure

patterns can only speak in terms of & and \^. shared things match both.

we statically type the rules in scout. this is for three purposes:

-   So we can figure out that #R.kind is getting a coord\'s kind.
    > Currently only coords have .kind but we can easily add others in
    > the future.

-   So we can figure out which function we\'re calling when we say
    > \"exists\".

-   To give earlier errors when we can. if we detect we\'re feeding a
    > \'mut\' into exists, then it\'s kind of nice to get an early error
    > about that.

-   We want to have overloading for our templates. For example, i might
    > want to have Array:(inl, Marine) and Array:(Marine).

these are all pretty weak reasons though\...

maybe it will help to know that something is a pack, so we can identify
syntax errors a lot sooner, like when we try to expand a non-pack.

why do we need the hashes in:

fn moo:(#A, #B) (a: #A, b: #B) { \... }

why cant we just do either:

-   fn moo:(A, B) (a: A, b: B) { \... }

-   fn moo:(A, B) (a: #A, b: #B) { \... }

the latter is weird and inconsistent.

the former means we *have* to have all the used runes in the list, we
*have* to have a list for every template. we wont be able to do:

fn moo(a: #A, b: #B) { \... }

and that would be unfortunate.

**Lets Dont Need Ordered Runes**

**LDNOR**

For the same reason as impls dont, see IDNOR.

**Must Remember Original Rune Ordering**

**MRORO**

The identifying runes for this function should be B and A, as if we said
moo:(B, A).

> fn moo(x: Map:#B, y: List:#A) { \... }

However, when we send that through the scout, it gets turned into:

> fn moo(x: #X, y: #Y)
>
> where {
>
> #X = List:B;
>
> #Y = List:A;
>
> }
>
> { \... }

Uh oh, now the identifying runes are X and Y. Unless we include the
rules too, in which case it\'s A, B, X, Y.

Because of this, we have to remember the original rune ordering.

Alternate approach: Require that any rune that appears in the parameter
list must be in the identifying runes list. Seems a waste though.

**Can\'t Identify Functions From Return Types**

**CIFFRT**

We can\'t overload based on return type. We can\'t, for example, say:

> let list: List:Int = List();

because that would require figuring out which List constructor to call
based on the return type we want.

No particular technical reason; we might be able to make this happen in
the future. It would be quite embellient. Some parts of the code are
built on this assumption, and those would have to be changed.

# Alternative: Nohash Approach

There\'s a possible alternative syntax, without needing the hash symbol
to signal that something\'s a rune.

> fn sum:T(a: T) = a + b;

We know that the T in a: T is a rune because it\'s specified in the
\"identifying runes\" list, right after the sum:. A rune can also be
introduced in the rules:

> fn sum(a: X) Void
>
> where(let X = MyList:Int)
>
> = a + b;

X is a rune because it\'s in a let clause in the rules.

## Comparing Functions

The current form is like:

> fn sum(a: #T, b: #T) #T = a + b;

without hashes, we\'d have:

> fn sum:T(a: T, b: T) T = a + b;

The benefit to the nohash approach is that it\'s easier to see the order
of the runes.

> fn map:(F, L)(list: L, func: F) { \... }

It\'s obvious here that when we say map:(print, List:Int), F will be
print and L will be List:Int. With the current approach,

> fn map(list: #L, func: #F) { \... }

it\'s not clear. Note that this is mitigated by the fact that we *can*
specify the order still:

> fn map:(#F, #L)(list: #L, func: #F) { \... }

so it\'s not that big of a problem.

## Comparing Lets

We\'ll probably never see anyone introducing runes in lets, so lets not
dwell too much on this.

The current form is like:

> let:T :&List:T = &myList;

but with the hash approach, it would look like:

> let :&List:#T = &myList;

Hash approach wins here.

## Comparing Impls

The current form is like:

> impl:T MyStruct:T for MyInterface:T;

but with the hash approach, it would look like:

> impl MyStruct:#T for MyInterface:#T;

Note that we wouldn\'t need to have impl:#T because we\'ll never need to
identify impls, see IDNOR.

Hash approach wins here.

## Comparing Templated Closures

The current form would be like:

> myList.reduce({:T(a: T, b: T) a + b})

but with the hash approach, it would look like:

> myList.reduce({(a: #T, b: #T) a + b})

Hash approach wins here.

## Handling Rune Reordering

Because of MRORO, we must remember the original rune ordering.

The alternate approach is to require that any rune that appears in the
parameter list **must** be in the identifying runes list. This is a much
more palatable solution in the nohash approach than it is with the hash
approach.

It\'s kind of weird because we\'d have to specify the runes on lets and
impls, like:

> impl:T MyStruct:T for MyInterface:T;

and

> let:T :&List:T = &myList;

but not too weird, because Rust already does their impl like this, and
barely anyone will use this advanced kind of let form.

# Uncategorized Notes

**Impls Dont Need Ordered Runes**

**IDNOR**

Functions, structs, and interfaces have identifying tames. Impls don\'t
because one cannot call an impl explicitly like that.

> impl:T MyStruct:T for MyInterface:T

doesnt make sense to have that first T. Might as well be:

> impl MyStruct:#T for MyInterface:#T

The only reason we had it for structs and functions is because we wanted
to identify them like: moo:Int. we\'ll never be identifying an impl like
that.
