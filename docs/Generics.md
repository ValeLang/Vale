
# Basic Concepts

Back when we did templates, every time we used (resolved) a struct, we would lazily compile it for that set of template args, unless we've seen that combination before.

Now, we **compile** them all\* ahead of time, outside of any particular use. (\* Except for lambdas, those are still templates, see UINIT and LAGT.)


# Some Rules Only Apply to Call Site or Definition (SROACSD)

This snippet is using a function bound:

```
func launch<X>(x X)
where func foo(int)void {
  ...
}
```

When we do a `func foo(int)void` rule, that's actually making three rules under the hood.

The first one is a `DefinitionFunc` rule, and it just generates a `Prototype` for its result rune, and puts it in the env. That way, the body can call that function. **This rule is used when compiling the function itself, it's not used at the call site.**

In the call site, instead we have a **Resolve** rule which looks in the current environment and grabs the actual `Prototype` that matches, and we have a **CallSiteFunc** rule which checks that its params and returns match what we expect. **These rules are used at the call site, but not used when compiling the function itself.**

In other words, some rules only apply to the call site, and some rules only apply when compiling the definition. There's some filtering that makes this happen.



# Call Site Solving Needs Caller Env (CSSNCE)

We have functions like:

```
func xoo<X>(x X) void
where func drop(X)void
{
  zoo(x);
}

func zoo<Z>(z Z) void
where func drop(Z)void
{
  ...
}
```

We're trying to solve `zoo(x)`. `zoo` requires a `func drop(Z)void` but that only exists in `xoo`'s environment.

So, the callee needs access to the caller's environment.


It only needs the caller's environment for bounds, it should not grab an actual function from the caller's environment. Any actual functions need to be grabbed from the environments of the types being passed in.


# Only Work with Placeholders From the Root Denizen (OWPFRD)

Let's say we're in this function:

```
struct SomeStruct<X> {
  x X;
}
func myFunc<T>(t T) {
  thing = SomeStruct<int>(7);
  z = thing.x;
  println(z);
}
```

The type of `z` is `int`, of course.

`SomeStruct.x` (of the template) is of type SomeStruct$0. We did a substitution to go from SomeStruct$0 to int.

If we don't do that substitution, then a `SomeStruct$0` creeps into our `myFunc` and wreaks absolute havoc, because then we're likely confusing it with myFunc's own placeholder, `myFunc$0`.

This is the reason we prefix placeholders with the name of their container (both in FullNameT and here in the docs when talking about them).

We also have a sanity check in the solver to make sure that we're never dealing with foreign placeholders, search for OWPFRD.


# Struct Can Impl Interface Multiple Times (SCIIMT)

A struct might implement an interface in multiple ways.

For example `MyController<T>` might implement `IObserver<SignalA>` and `IObserver<SignalB>`, so there would be two ImplT's for it:

 * `ImplT(MyController, MyController<Placeholder(0)>, IObserver, IObserver<SignalA>)`
 * `ImplT(MyController, MyController<Placeholder(0)>, IObserver, IObserver<SignalB>)`


## Must Look Up Impls By Template Name (MLUIBTN)

The above (SCIIMT) also means that one cannot just look for `ImplT(_, _, _, IObserver<Placeholder(0)>`, as there are none in that table.

They should instead search by the template name, like `ImplT(_, _, IObserver, _)`.


# Structs Member Rules Are Skipped During Resolving (SMRASDR)

When we have a recursive type like this:

```
struct ListNode<T> {
  val T;
  next Opt<ListNode<T>>;
}
```

when we try to resolve the `Opt<ListNode<T>>` it will try to resolve the `ListNode<T>` which runs all these rules _again_, and goes into an infinite loop.

The answer is to not run the innars' rules when we're resolving. Only run the innards when compiling the definition.


# Require Explicit Multiple Upcasting to Indirect Descendants and Ancestors (REMUIDDA)

If we have a Serenity which impls IFirefly which impls IShip:

```
interface IShip { }

interface IFirefly { }
impl IFirefly for IShip { }

struct Serenity { }
impl Serenity for IFirefly { }
```

Then we can't directly upcast a Serenity to an IShip, like:

```
ship IShip = Serenity();
```

We'll have to upcast it to an IFirefly first:

```
ship IShip = Serenity.as<IFirefly>();
```

This is just to save some compile speed. This way, we can just index the direct parents and children of structs and interfaces, and don't have to do any transitive calculations, for example to find out if a struct indirectly implements an interface.

This will likely also save us some space and complexity in the vtables; each vtable won't need *all* the descendants and ancestors.



# Don't Use Default Expression When Compiling Denizen (DUDEWCD)

When compiling definitions, we need to always populate placeholders for every argument, and never use default expressions.

Let's say we have:

```
struct Thing<N Int = 5> {
  vec Vec<N, Float>;
}
```

When we compile the innards of that, we don't want to assume that N is 5, because it could be anything.

So, we need to *not* execute that LiteralSR(N, 5) rule.


# Using Instantiated Names in Templar (UINIT)


This one little name field (FunctionHeaderT.fullName) can illuminate much of how the compiler works.

For ordinary functions, each ordinary FunctionA becomes one ordinary FunctionT/FunctionHeaderT.

 * This IFunctionNameT's parameters have the types you'd expect
 * This IFunctionNameT will have no templateArgs.

For generic functions, each generic FunctionA becomes one ordinary FunctionT/FunctionHeaderT.

 * This IFunctionNameT's parameters will have some PlaceholderTs or PlaceholderTemplatas in them.
 * The templateArgs will all PlaceholderTemplatas.

Lambdas are where it gets interesting. One lambda can manifest multiple FunctionTs/FunctionHeaderTs. For example:

```
lam = x => println(x);
lam(3);
lam(true);
```

  This will actually manifest *two* FunctionTs/FunctionHeaderTs:

 * One for line 2, with one parameter (int) and one template arg (int).
 * One for line 3, with one parameter (bool) and one template arg (bool).

We also use this same scheme for the CompilerOutputs, to map names to environments.


# Concept Functions With Generics (CFWG)

Our current concept functions don't really work with default generic parameters that well.

Here's how it should work in a post-generics world.



## Prototype-Based Concept Functions

```
struct Functor1<P1 Ref, R Ref, F Prot = func moo(P1)R> {
}
```

No need of placeholders. We just go in and start figuring shit out.

We start solving. We never know P1, R, or F, but we at least know that we can call a function named moo on things of those types.

func moo(P1)R becomes:

 * ResolveSR, which looks through the overload indexes for a single `moo` that fits the name and param requirements, even though we dont fully know the requirements yet.
 * CallSiteFuncSR, which grabs its return type and equates it
 * DefinitionSiteFuncSR, which just puts into the env the knowledge that this is a call that can be made.



## Placeholder-Based Concept Functions

```
struct Functor1<P1 Ref, R Ref, F Ref> {
  functor F = func moo(P1)R;
}
```

```
func Functor1<P1, R, F>(functor F = func moo(P1)R) Functor1<P1, R, F> {
}
```

func moo(P1)R becomes:

 * ResolveSR(X Ref, moo, P1, R);
   runs when P1 and R are defined.
   it will create an OverloadSet pointing at moo and put it in X.
   the templex produces X ref as the result rune, so it ends up equal to F.
 * DefinitionFuncSR(Z Prot, X, P1, R);
   runs when X, P1, and R are all defined.
   X is fed in as a placeholder, remember.
   which puts the prototype Z into the environment to establish that X is callable.
 * CallsiteFuncSR(Z Prot, X Ref, P1, R)
   runs when X, P1, and R are all defined.
   X will be an overload set from resolve-func, or a `__call`able type from user.
   it makes sure that there is indeed a `__call` prototype taking X and P1 and returns R.
   it assigns it into Z. it doesnt _really_ have to, but it means Z is always defined which is simpler.


sucks that we need a functor. but honestly, that comes from this weird placeholder crap. if we didnt need placeholders, we could do this easier.



# PlaceholderTemplata, PlaceholderKind, Coords, Kinds (PTPKCK)

When we compile a generic denizen's definition, we conjure up some placeholders.


## Basic Case: Integers

Let's say we have a "repeat" function, generic on how many times to repeat:

```
func repeat<N int>(s str) {
  foreach _ in 0..N {
    println(s);
  }
}
```

We'll conjure up a `PlaceholderTemplata(repeat$0, IntegerTemplataType)` to serve as our `N`.


Later on in the instantiator, we'll see who calls `repeat` with what actual integers, and we'll do the substitution then.


Any kind of templata works like this... except for kinds.


## More Complicated Case: Kinds and Coords

Let's say we wanted to take in a generic type parameter:

```
func get<T>(list List<T>, i int) T {
  result = list.array[i];
  return result;
}
```

In a perfect world, perhaps our locals would contain `ITemplata[CoordTemplataType]`, but alas, they only contain `CoordT`s.

To work around that, we make a special kind, `PlaceholderKindT`. If a CoordT contains a `PlaceholderKindT`, then that's the same thing as a "coord placeholder" so to speak.

At some point, we'll make our locals, members, etc. all contain ITemplatas so we don't need this special kind.

Perhaps there's even a way to have everything be just a rune, and all their templatas would actually just be in the env. This would fit well with a world where the entire function is one giant rule solve. It might even help partially evaluate a call *enough* that we can narrow down to one overload, to help figure out the types in the caller too.


## Incrementally Reluctantly Add Generic Placeholders (IRAGP)

We have two functions for making arrays, one for mutable and one for immutable.

```
extern("vale_runtime_sized_array_mut_new")
func Array<M, E>(size int) []<M>E
where M Mutability = mut, E Ref;
```

They're distinguished by the `= mut` and `= imm`.

However, when we're compiling the functions, we hand in some placeholders which immediately conflict with these rules.

For that reason, we do some solving before filling in any placeholders.

(Long term, we might want to consider outlawing specialization like this)

In fact, we do solving *between* adding placeholders as well. If we just populate them all at the same time, this would fail:

```
func bork<T, Y>(a T) Y where T = Y { return a; }
```

Because the placeholder for T is of course not equial to the placeholder for Y.

So, we populate placeholders one at a time, doing a solve inbetween each new one.

This should also help when we switch to regions, where we want to say that two generic coords
share the same region.




# Solve First With Predictions, Resolve Later (SFWPRL)

Sometimes, the `func drop(T)void` rule doesn't run soon enough for us. For example:

```
sealed interface Opt<T Ref>
where func drop(T)void
{ }

struct Some<T>
where func drop(T)void
{ x T; }

impl<T> Opt<T> for Some<T>
where func drop(T)void;
```

Here, when we're compiling that impl, two things need to happen:

 * The DefinitionFuncSR puts the `drop` function into the environment.
 * We resolve the `Some<T>`, which requires that a `drop` function exists in the environment.

There are two ways we might solve this:

 * Somehow force the drop rule to run sooner.
 * Force the resolve of the `Some<T>` to happen later.

The former might require some sort of priority mechanism in the solver, so we're doing the latter.

Basically, during a solve, if the solver wants to resolve an interface or struct, it doesn't actually call the StructCompiler's `resolveStruct`/`resolveInterface` which checks all the requirements are there.

Instead, it will call StructCompiler's `predictStruct`/`predictInterface` which do very little, they mostly just create a name, plus solve some default generic parameters.

Later, after the solve, we go back through and do the actual `resolveStruct`/`resolveInterface`.

We do this with functions too. ResolveSR will actually just create a prototype out of thin air. It's only afterward that we actually go and find it. (This is also why ResolveSR needs a return type rune)



# Solving Then Checking Must Be Different Phases (STCMBDP)

When compiling a definition, we declare that certain concept functions exist.

There's a cyclical dependency here though:

 1. To declare that a function exists, we need to know the actual types of the params and returns.
 2. To know the actual types we're resolving, we need to check their requirements.
 3. To check their requirements, we need to know that a function actualy exists in our scope.

One of those dependencies needs to be changed up.

 1. We might be able to change this if we could make our system operate on partial data.
 2. We can check their requirements later.
 3. This can't be changed.

#2 seems easiest for now.

So, when compiling a denizen, we do all the checking of calls _later_.



# Need Function Inner Env For Resolving Overrides (NFIEFRO)

Let's say we have these functions:

```
abstract func drop<T>(x Opt<T>)
where func drop(T)void;

func drop<T>(x Some<T>)
where func drop(T)void {
  [_] = x;
}
```

When we're figuring out the itables, we see the abstract one, and so we try to resolve for the one taking a `Some<T>`.

However, when we do that, it failed the second `where func drop(T)void` there. That's because when the abstract function is "calling" the override, it didn't have the abstract function's original environment which contained the knowledge that there exists a `func drop(T)void`.

So, we need to recall the abstract function's inner environment when we do that resolve. To do that, we need to track the inner env in the CompilerOutputs.



## Must Know Runes From Above (MKRFA)

When we start evaluating a function or struct or something, we don't know the values of its runes.

For example:

```
fn add<T>(list &List<T>, elem T) { ... }
```

we don't know the value of T, we're figuring it out now.

One would think that whenever we see a CodeRuneS("T"), it's an
unknown.

**Case 1: Manually Specifying a Rune**

If we have this function:

```
fn moo<T>(x T) Some<T> {
  Some<T>(x)
}
```

We need to look up that T from the environment.

For that reason, Scout's IEnvironment will track which runes are currently known.

**Case 2: Runes from Parent**

If we have this `__call` function:

```
interface IFunction1<M, P1 Ref, R Ref> M {
  fn __call(virtual this &IFunction1<M, P1, R>, param P1) R;
}
```

If we want to evaluate it, we can actually infer the M, P1, and R from
the first argument. So they can be regular runes.

**Conclusion**

The PostParser will keep track of what runes are defined in parent environments. When it encounters one, it'll know it's a rune, and add a RuneParentEnvLookupSR rule for it. For Case 1, we'll just leave it in. For Case 2, we'll immediately strip it out.

When compiling an expression (like case 1) we'll preprocess the RuneParentEnvLookupSR rule out, to populate its value from the environment.


# Can't Get All Descendants Of Interface (CGADOI)

Let's say we have a `MyStruct` implementing interface `MyObserver<int>`:

If we want to know all children for `MyObserver`, would we count this? It's hard to say.

For now, we leave that question unanswered, and say that we can never know all children for a specific interface template.


# Need Bound Information From Parameters (NBIFP née NBIFPR)

Let's say we have this code:

```
#!DeriveStructDrop
struct BorkForwarder<LamT>
where func __call(&LamT)int {
  lam LamT;
}

func bork<LamT>(self &BorkForwarder<LamT>) int {
  return (self.lam)();
}

exported func main() {
  b = BorkForwarder({ 7 });
  b.bork();
  [_] = b;
}
```

This fails on `(self.lam)()` because `bork` itself doesn't know that there exists a `__call(&Lam)int`.

Two possible solutions:

 1. Require the user add bounds to `func bork` (and all other callers) too.
 2. Infer that there's a `__call(&Lam)int` from the existence of `BorkForwarder<Lam>` which requires it.

We can't always do 1 because sometimes the caller is an abstract function (see ONBIFS).

So, we'll need to do #2.

A few places we'll need to do this:

 * At the beginning of the current denizen, where we introduce the placeholders. We scour all of the requirements imposed by all of the parameters (like the `BorkForwarder<LamT>` that requires `__call(&LamT)int`) and create prototypes for them. (See also [Rust #2089](https://github.com/rust-lang/rfcs/pull/2089))
 * When an abstract function is "calling" an override, we'll need to incorporate the bounds for the overriding struct. (See ONBIFS)
 * In a match's case statement, when we mention a type, we need to incorporate the bounds from that type.


### ... but not return types.

Note that while we can incorporate bounds from parameters, we can't incorporate any from return types. We used to do this, and in this example:

```
func HashMap<K Ref imm, V, H, E>(hasher H, equator E) HashMap<K, V, H, E> {
  return HashMap<K, V, H, E>(hasher, equator, 0);
}
```

It failed because `main` wasn't passing any functions to satisfy the bounds which were expected by the `HashMap<K, V, H, E>(hasher, equator, 0)` invocation (it expected a `drop(H)`). At best, we can hoist the _requirements_ from the return type, but we can't use the return type as evidence that a type satisfies some bounds.


### Monomorphizer

The instantiator also needs to do this. This example (search NBIFP for test case) shows why:

```
struct IntHasher { }
func __call(this &IntHasher, x int) int { return x; }

#!DeriveStructDrop
struct HashMap<H> where func(&H, int)int {
  hasher H;
}

func moo<H>(self &HashMap<H>) {
  // Nothing needed in here to cause the bug
}

exported func main() int {
  m = HashMap(IntHasher());
  moo(&m);
  destruct m;
  return 9;
}
```

When we instantiate `moo`, we're given 


# Substitute Bounds In Things Accessed From Dots (SBITAFD)

When we access something with a dot, we do a substitution so that we know what it is in terms of the current denizen's placeholders. For example, in:

```
#!DeriveStructDrop
struct HashMapNode<X Ref imm> {
  key X;
}

#!DeriveStructDrop
struct HashMap<T Ref imm> {
  table! Array<mut, HashMapNode<T>>;
}

func keys<K Ref imm>(self &HashMap<K>) {
  self.table.len();
}

exported func main() int {
  m = HashMap<int>([]HashMapNode<int>(0));
  m.keys();
  [arr] = m;
  [] = arr;
  return 1337;
}
```

in `keys`, when we do `self.table`, that's accessing a

`Array<mut, HashMapNode<HashMap$T>>` and turning it into a

`Array<mut, HashMapNode<keys$K>>`. It's rephrasing it in more familiar terms, in other words. This is "substitution" and happens inside substituter currently.


Every instantiation (such as `HashMapNode<HashMap$T>`) has some instantiation bounds, registered with the coutputs. This also means that `HashMapNode<keys$K>` needs some instantiation bounds registered, because it is also an instantiation.

This means that we need to register some new instantiation bounds when we turn the `HashMapNode<HashMap$T>` into the new `HashMapNode<keys$K>`. We do that in the substituter.


# Overrides Need Bound Information From Structs (ONBIFS)

This is a special case of NBIFPR, where an abstract function is trying to resolve an override which has some requirements.

```
sealed interface Bork {
  func bork(virtual self &Bork) int;
}

struct BorkForwarder<Lam>
where func drop(Lam)void, func __call(&Lam)int {
  lam Lam;
}

impl<Lam> Bork for BorkForwarder<Lam>
where func drop(Lam)void, func __call(&Lam)int;

func bork<Lam>(self &BorkForwarder<Lam>) int
where func drop(Lam)void, func __call(&Lam)int {
  return (&self.lam)();
}

exported func main() int {
  f = BorkForwarder({ 7 });
  return f.bork();
}
```

This failed while trying to assemble the itables.

When we were figuring out the vtable for `BorkForwarder<Lam1>`, we were trying to find its override for `bork`.

We looked for a `bork(BorkForwarder<Lam1>)`. (Aside: because of NAFEWRO we looked from the perspective of `bork(Bork)`, we used its environment.)

However, `func bork(BorkForwarder<Lam1>)` has a requirement that there's a `func __call(&Lam)int`, but the call site (the abstract function `bork(Bork)`) had no knowledge of such a function, so it failed.

This reinforces that we need to solve NBIFPR by gathering information from elsewhere (parameters).


# Functions Must Be Associated With Type to be Used in Function Bounds (FMBAWTUFB)

We can't just pass any old function in for a function bound. It must be associated with the type somehow. Otherwise, if we have a struct and function like this:

```
struct Bork<T>
where func zork(T)void {
  ...
}

func moo(bork Bork<T>)
where func zork(T)void {
  ...
}
```

and then we store that `Bork<T>` into a global (or some other object somewhere), and then later retrieve it, we'll have no idea what its `zork` is.



# Tuples And Variadics With Generics (TAVWG)

Tuples have a variadic member:

```
struct Tup<T RefList> {
  ..T;
}
```

It would be easy to work with these if we knew the actual instantiation, like if we were currently dealing with a Tup<(int, bool)> we could reasonably determine what myTup.0 is, or how to destroy myTup.

However, we need to be able to write generic functions for tuples, for example drop. It would look something like:

```
func drop<T RefList>(tup Tup<T>)
where func drop(T...)void {
  (tup)...drop();
}
```

which means we need some sort of "move ellipsis" operator and something to check the bounds on each T.

Alas, that's likely too much work for now, we'll have to fall back to having a two-element tuple and come back to them.


# Lambdas Are Generic Templates (LAGT)

Lambdas are instantiated every time they're called. In this:

```
func genFunc<T>(a &T) &T {
  f = x => a; // Lambda struct at code loc 2:6
  f(true);
  f(a);
  f(7)
}
exported func main() int {
  genFunc(7)
}
```

There are actually three generic functions created:

 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{bool}`
 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{genFunc$0}`
 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{int}`

These are each generic functions, just like a normal `func moo<T>(a T) { ... }`/`moo<moo$0>` generic function. These just have an extra disambiguation, these are **not** instantiations.

They are disambiguated by the "generic template args" (eg `{bool}`). It's just a list of coords to disambiguate them from each other. These are not generic args, they are generic **template** args.

In this program, we're calling only `genFunc(7)` so after the instantiator pass these would be the three final instantiations in total:

 * `mvtest/genFunc<int>.lam:2:6.__call{bool}<bool>`
 * `mvtest/genFunc<int>.lam:2:6.__call{int}<int>`
 * `mvtest/genFunc<int>.lam:2:6.__call{int}<int>` (duplicate!)

The generic template args are usually redundant with the actual parameters, so we don't include a `(bool)` at the end of the name like we usually do for function names.

However, theyre not necessarily redundant with the template args. If we had a `(a int, b)` then the lambda has only one generic arg, the implicit one for b. That thing's name might be `__call{int, bool}<bool>`.

Serendipitously, this approach will result in the same ending instantiation name for those latter two, so we don't have to instantiate that lambda an extra time needlessly.


If we also called `genFunc("hello")` we'd have these 6 in total:

 * `mvtest/genFunc<int>.lam:2:6.__call{bool}<bool>`
 * `mvtest/genFunc<int>.lam:2:6.__call{int}<int>`
 * `mvtest/genFunc<int>.lam:2:6.__call{int}<int>`
 * `mvtest/genFunc<str>.lam:2:6.__call{bool}<bool>`
 * `mvtest/genFunc<str>.lam:2:6.__call{str}<str>`
 * `mvtest/genFunc<str>.lam:2:6.__call{int}<int>`

So in a way, a generic is a template that makes a generic function. That one `x => a` is a template which formed three generic functions. Each of those generic functions was instantiated twice, so we have six in total.


## Lambdas Have Placeholders from Containing Top Level Denizen (LHPCTLD)

Look at LAGT's example, and we see that lambdas never create placeholders for themselves; there are never any lambda parameter placeholders.

They do however sometimes use placeholders from their parent function, such as the `mvtest/genFunc<genFunc$0>.lam:2:6.__call{genFunc$0}`.

Another example, this program has a lambda inside a generic function:

```
func genFunc<T>(a &T) &T {
  return { a }();
}
exported func main() int {
  genFunc(7)
}
```

The function `genFunc.lam:2:10`, is loading `a` whose type is actually the containing genFunc's 0th placeholder.

In other words, lambda functions load placeholders from a different function (their parent function).



## Getting Lambda Instantiation's Original Generic's Name (GLIOGN)

Normally, if we have a PrototypeT's full name, it's pretty easy to get its original template's full name. Take a FullNameT[IFunctionNameT]'s local name (the IFunctionNameT) and just call .template on it.

However, that doesn't work for lambdas, which are templates rather than generics.

```
func genFunc<T>(a &T) &T {
  f = x => a; // Lambda struct at code loc 2:6
  f(true);
  f(a);
  f(7)
}
exported func main() int {
  genFunc(7)
}
```

Here, there are actually three generic functions created:

 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{bool}`
 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{genFunc$0}`
 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{int}`

In this program, we're calling only `genFunc(7)` so these would be the final three instantiations in total:

 * `mvtest/genFunc<int>.lam:2:6.__call{bool}<bool>`
 * `mvtest/genFunc<int>.lam:2:6.__call{genFunc$0}<int>`
 * `mvtest/genFunc<int>.lam:2:6.__call{int}<int>`

Note how `{genFunc$0}` is still there, even in an instantiation name. See DMPOGN for why.
  
These all might be completely different functions, depending on what happened inside the body, what kind of metaprogramming it does, etc.

Now the challenge: What is `mvtest/genFunc<int>.lam:2:6.__call{int}<int>`'s original generic's name? We have to be careful because these look very similar:

 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{genFunc$0}`
 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{int}`

It's actually the second one.

We can find that second one by comparing all their generic full names. The generic full name is where we remove any template args (`<...>`) and parameters (`(...)`) from every name in the full name.

`mvtest/genFunc<int>.lam:2:6.__call{int}<int>` becomes `mvtest/genFunc.lam:2:6.__call{int}`.

The two candidates become:
 * `mvtest/genFunc.lam:2:6.__call{genFunc$0}`
 * `mvtest/genFunclam:2:6.__call{int}`

The second one matches nicely!

And that's how we find the original generic function for a particular instantiation.

## Don't Monomorphize Parts Of Generic Names (DMPOGN)

Because of GLIOGN, we need a lambda function to remember its original generic.

There was an interesting oddity, where we had an instantiated name that contained a placeholder: `mvtest/genFunc<int>.lam:2:6.__call{genFunc$0}<int>`.

This section explains why that's important, by showing what happens if we don't.

Let's say we have a similar program, but calling `genFunc(str)`

```
func genFunc<T>(a &T) &T {
  f = x => a; // Lambda struct at code loc 2:6
  f(true);
  f(a);
  f(7)
}
exported func main() int {
  genFunc("hello")
}
```

We have those same three generic functions coming from the typing pass:

 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{bool}`
 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{genFunc$0}`
 * `mvtest/genFunc<genFunc$0>.lam:2:6.__call{int}`

But now the challenge is: What is `mvtest/genFunc<str>.lam:2:6.__call{str}<str>`'s original generic's name?

We apply the same rules to get the generic full name, and end up with `mvtest/genFunc.lam:2:6.__call{str}`. **However, that doesn't exist.** There is no generic by that name.

This happened because we were looking for something nonsensical. How did that `__call{str}` even happen, and why were we looking for something with that? It's because our translator in instantiator was blindly replacing *all* placeholders.

Moral of the story: It shouldn't replace placeholders in generic names, such as `__call{genFunc$0)`. It should leave that alone, so that we instead looked for `mvtest/genFunc<str>.lam:2:6.__call{$genFunc0}<str>`.


# Must Specify Array Element (MSAE)

We no longer support grabbing a prototype's return type, so we can no longer say:

`Array<mut>(5, x => x)`

It previously would look at the incoming lambda, send it an argument type, and grab the return value. We'll need to add that back in.

For now, we have to specify the type:

`Array<mut, int>(5, x => x)`


# Lambdas Can Call Parents' Generic Bounds (LCCPGB)

If we have a lambda in a generic function:

```
func genFunc<T>(a &T)
where func print(&T)void {
  { print(a); }()
}
exported func main() {
  genFunc("hello");
}
```

Then when we monomorphize the lambda, the instantiator needs to remember the supplied `print` from `genFunc`'s caller.


## Lambdas and Children Need Bound Arguments From Above (LCNBAFA)

When we're monomorphizing a lambda, it will be calling function bounds that came from the parent function. So, the instantiator needs to convey those downward when we stamp a lambda generic template.


For example, when we're inside

`add<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher)`

it will want to call 

`add:204<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher>).lam:281` which might call `HashMap.bound:__call<>(&add$2, @add$0)int`. But the lambda itself doesn't know any bounds... it really should contain the mapping `IntHasher.__call<>(&IntHasher, int)` ->
`HashMap.bound:__call<>(&add$2, @add$0)int` somehow.

So in the instantiator, when a function tries to instantiate something beginning with its own name, it will pass down its own bounds into it as well.


Additionally, we run into the same problem with child functions of the lambda (and likely will again with interfaces' child functions).

`add<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher)`

Will want to call this `drop` function:

```
add:204<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher>)
.lam:281
.drop<>(@add:204<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher>).lam:281)
```

so it tries to monomorphize that. It takes a `@add:204<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher>).lam:281` argument, which has a template arg of `HashMap<int, int, ^IntHasher>`, but when it sees that it doesn't see that we satisfied its bounds (similar to above) so it dies.

The solution is the same: in the instantiator, when a function tries to instantiate something beginning with its own name, it will pass down its own bounds into it as well.




# WTF Is Going On With Impls (WIGOWI)

For each impl, the typing phase will identify all the sub citizen's overrides for the interface. It's probably one of the most complex areas of the compiler.


Recall:

```
interface ISpaceship<E, F, G> { ... }
abstract func moo<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X) where exists drop(Y)void;

struct Raza<A, B, C> { ... }
impl<I, J> ISpaceship<int, I, J> for Raza<I, J>;

func launch<Y, Z>(self &Raza<Y, Z>, bork int) where exists drop(Y)void { ... }
```

Right now we have ISpaceship, launch, and the impl ("ri").

We want to locate that launch/Raza override, similarly to this conceptual "dispatcher" function:

```
func launch<Y, Z>(virtual self &ISpaceship<int, Y, Z>, bork int)
where exists drop(Y)void {
  self match {
    raza &Raza<Y, Z> => launch(raza, bork)
  }
}
```

The first step is figuring out the dispatcher's inner environment, so we can later use it to resolve our `launch` override.


## Step 1: Get The Compiled Impl's Interface, In Terms of Dispatcher (GTCII)

Start by compiling the impl, supplying any placeholders for it.

```
impl<I, J> ISpaceship<int, I, J> for Raza<I, J>;
```

becomes:

```
impl<ri$0, ri$1> ISpaceship<int, ri$0, ri$1> for Raza<ri$0, ri$1>
```

Note that we aren't actually doing this in EdgeCompiler, this actually happens when we first compile the impl.

Now that we have it, rephrase the placeholders to be in terms of the dispatcher's case (so instead of `ri$0`, think of it as `dis$0`). So now we have:

```
ISpaceship<int, dis$0, dis$1>
```

We'll refer to this as the "dispatcher interface".


## Step 2: Compile Dispatcher Function Given Interface (CDFGI)


Now, take the original abstract function:

```
abstract func launch<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X) where exists drop(Y)void;
```

and try to compile it given the dispatcher interface (`ISpaceship<int, ri$0, ri$1>`)
as the first parameter.

```
abstract func launch(self ISpaceship<int, dis$0, dis$1>, bork int) where exists drop(dis$0)void;
```

We're conceptually compiling a match's case, from which we'll resolve a function. In a way, we compiled it as if `X` = `int`, `Y` = `dis$0`, `Z` = `dis$1`.


We did this because:

 * We wanted that `bork X` to become a `bork int`.
 * The abstract function had bounds (`drop(T)void`) that we wanted to translate (`drop(dis$0)void`).


And now, we have our inner environment from which we can resolve some overloads!


## Some More Complex Cases

Our goal now is to figure out the override functions. Imagine we have these interfaces and impls:

```
interface ISpaceship<E, F, G> { ... }

struct Serenity<A, B, C> { ... }
impl<H, I, J> ISpaceship<H, I, J> for Serenity<H, I, J>;

struct Firefly<A, B, C> { ... }
impl<H, I, J> ISpaceship<J, I, H> for Firefly<H, I, J>;
// Note the weird order here ^

struct Raza<B, C> { ... }
impl<I, J> ISpaceship<int, I, J> for Raza<I, J>;
// Note this int here ^

struct Milano<A, B, C, D> { ... }
impl<I, J, K, L> ISpaceship<I, J, K> for Milano<I, J, K, L>;
// Note that Milano has more params than ISpaceship.
// This is like a MyStruct<T> implementing a IIntObserver.

struct Enterprise<A> { ... }
impl<H> ISpaceship<H, H, H> for Enterprise<H>;
// Note they're all the same type
```

If we have an abstract function:

```
abstract func launch<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X) where exists drop(X)void;
```

and these overrides:

```
func launch<X, Y, Z>(self &Serenity<X, Y, Z>, bork X) where exists drop(X)void { ... }
func launch<X, Y, Z>(self &Firefly<X, Y, Z>, bork X) where exists drop(X)void { ... }
func launch<Y, Z>(self &Raza<Y, Z>, bork int) { ... }
func launch<X, Y, Z, ZZ>(self &Milano<X, Y, Z, ZZ>, bork X) where exists drop(X)void { ... }
func launch<X>(self &Enterprise<X>, bork X) where exists drop(X)void { ... }
```

...we need to find those overrides.

To do it, recall that we're *conceptually* lowering these abstract functions to match-dispatching
functions. We're not actually doing this, just thinking this way. One might be:

```
func launch<X, Y, Z>(virtual self &ISpaceship<X, Y, Z>, bork X)
where exists drop(X)void {
  self match {
    serenity &Serenity<X, Y, Z> => launch(serenity, bork)
    firefly &Firefly<Z, Y, X> => launch(firefly, bork)
    <ZZ> milano &Milano<X, Y, Z, ZZ> => launch(milano, bork) // See the end for wtf this is
    // Read on for why the other cases aren't here
  }
}
```

Raza and Enterprise have some assumptions about their generic args, so we'll need different
conceptual functions for them.

```
func launch<Y, Z>(virtual self &ISpaceship<int, Y, Z>, bork int) {
  self match {
    raza &Raza<Y, Z> => launch(raza, bork)
    // other cases unimportant for our purposes
  }
}

func launch<X>(virtual self &ISpaceship<X, X, X>, bork X)
where exists drop(X)void {
  self match {
    enterprise &Enterprise<X> => launch(enterprise, bork)
    // other cases unimportant for our purposes
  }
}
```

The reason we do all this is so we can do those resolves:

   * launch(serenity) is resolving launch(&Serenity<X, Y, Z>, X)
   * launch(firefly) is resolving launch(&Firefly<Z, Y, X>, X)
   * launch(raza) is resolving launch(&Raza<Y, Z>, int)
   * launch(enterprise) is resolving launch(&Enterprise<H>, X)

So, the below code does the important parts of the above conceptual functions.


## Step 3: Figure Out Dependent And Independent Runes (FODAIR)

AKA: Override Milano Case Needs Additional Generic Params (OMCNAGP)

Let's do some preparation work so that we can later handle Milano's case.

What's the Milano case?

Recall:

```
func launch<X, Y, Z>(virtual self &ISpaceship<X, Y, Z>, bork X)
where exists drop(X)void {
  self match {
    serenity &Serenity<X, Y, Z> => launch(serenity, bork)
    firefly &Firefly<Z, Y, X> => launch(firefly, bork)
    <ZZ> milano &Milano<X, Y, Z, ZZ> => launch(milano, bork)
  }
}
```

As you can see, it doesn't really fit into this whole match/enum paradigm.
There could be any number of Milano variants in there... ZZ could be int, or str, or bool,
or whatever.


First, we need to figure out what kind of extra runes we'll need, like this `<ZZ>`.

To do this, we solve the impl given only the interface part.

So if we have this impl:

```
impl<I, J, K, ZZ> ISpaceship<I, J, K> for Milano<I, J, K, ZZ>;
```

then we solve it given just an `ISpaceship<dis$0, dis$1, dis$2>`. We're left with this:

```
impl<dis$0, dis$1, dis$2, ZZ> ISpaceship<dis$0, dis$1, dis$2> for Milano<dis$0, dis$1, dis$2, ZZ>;
                          ^
                          ^--- unknown!
```

Hence, that `ZZ` is an "independent" rune. The rest, `I` `J` `K` are "dependent" runes.

This doesn't happen in EdgeCompiler, it actually happens in ImplCompiler and we just remember the independences of each rune.


## Step 4: Figure Out Struct For Case (FOSFC)


Now that we know the dispatcher interface and the independent runes, we have enough to figure out the sub citizen for the case (in other words, the "override struct").

For example, in the Milano case:

 * The dispatcher interface is a `ISpaceship<dis$0, dis$1, dis$2>`
 * The independent runes: `ZZ`.

First, make placeholders for all the independent runes. Here, `ZZ`'s will be `case$3`.


Then, feed it into the impl's solver as if we're "calling" the impl.

In the Milano case, we'd end up with a `Milano<dis$0, dis$1, dis$2, case$3>`.



NOTE TO SELF: we're not bringing in any impl bounds! this might be where we used to do that


### Inherit Bounds From Case Struct (IBFCS)


In FOSFC, we do a solve to get the case struct. When doing that, we also grab the reachable bounds from that struct.


For example, this is a parent interface that has no knowledge or assumptions of
being droppable:

```
#!DeriveInterfaceDrop
sealed interface ILaunchable {
  func launch(virtual self &ILaunchable) int;
}

#!DeriveStructDrop
struct Ship<T>
where func drop(Lam)void, func __call(&Lam)int {
  lam Lam;
}

impl<T> ILaunchable for Ship<T>;

func launch<T>(self &Ship<T>) int {
  return (self.lam)();
}
```

When resolving overrides for it, this is the conceptual case:

```
func launch(virtual self &ILaunchable) {
  self match {
    <ZZ> borky &Ship<ZZ> => bork(fwd)
  }
}
```

However, there's something subtle that's needed. The bork(fwd) call is trying to resolve
this function:

```
func launch<T>(self &Ship<T>) int
```

However, the `Ship<T>` invocation requires that Lam has a `drop`... which nobody
can guarantee.

But wait! We're taking an *existing* `Ship<T>` there. So we can know that the T already
supports a drop.

We do this for NBIFPR for parameters and returns and one day for cases inside matches.
Let's do it here for this conceptual case too.





## Step 5: Assemble the Case Environment For Resolving the Override (ACEFRO)

Step 2 gave us the dispatcher interface: `ISpaceship<dis$0, dis$1, dis$2>`

Step 4 gave us the case struct, `Milano<dis$0, dis$1, dis$2, case$3>`.

Now, let's put this information, and all the other information we solved for, into a new environment. This is the "case environment".

It's particularly necessary because it has any bounds that originally came from the abstract function or the case struct.

We'll be using this to resolve an overload later.


## Step 6: Use Case Environment to Find Override (UCEFO)


Recall the dispatcher function:

```
func launch<Y, Z>(virtual self &ISpaceship<int, Y, Z>, bork int)
where exists drop(Y)void {
  self match {
    raza &Raza<Y, Z> => launch(raza, bork)
    // other cases unimportant for our purposes
  }
}
```

Now we have the environment for the `=> launch(raza, bork)`!


And regarding in the Milano case:

```
<ZZ> milano &Milano<X, Y, Z, ZZ> => launch(milano, bork)
```

we also already determined the `ZZ` (we fed in a placeholder, `case$3`).


Now let's use this environment to resolve our overload. We look for:
```
launch(Raza<ri$0, ri$1>, int)
```
and sure enough, we find the override func:
```
func launch<P, Q>(self Raza<P, Q>, bork int) where exists drop(P)void;
```

Instantiating it is the next challenge, we'll do that below.

All this is done from the impl's perspective, the impl is the original calling
env for all these solves and resolves, and all these placeholders are phrased in
terms of impl placeholders (eg ri$0).


## Step 7: Monomorphization

AKA: Abstract Function Calls The Dispatcher (AFCTD)


Let's say we have this abstract func:

```
abstract func send<T>(self &IObs<T>, e T)
where D Prot = func drop(T)void
```

And this impl:

```
impl<X, ZZ> IObs<Opt<X>> for MyStruct<X, ZZ>
```

Then call the abstract function with `&IObs<Opt<dis$X>>` which is the interface from the impl but with its placeholders phrased as "dis" placeholders which stands for "dispatcher". We end up with this:

```
func ...(self &IObs<Opt<dis$X>>, e Opt<dis$X>)
where D Prot = func drop(Opt<dis$X>)void
```

Now fill in the name; it's the "dispatcher" and it takes in generic parameters similar to the impl. Think of this as an alternate way of compiling a function; we end up with something like a compiled function, that's phrased in terms of its own placeholders.

```
func dis<dis$X>(self &IObs<Opt<dis$X>>, e Opt<dis$X>)
where func drop(Opt<dis$X>)void
```

(Note that there's no `dis$ZZ` in here, we don't include independent runes, see OMCNAGP.)

So it's like the abstract function `send<T>` magically decided that its own `T` = `Opt<dis$X>`, and calls `dis` with it.

In the end, `send<T>` is calling `dis<dis$X>(&IObs<Opt<dis$X>>, Opt<dis$X>)` and sends instantiation bounds `D` = `func drop(send$T)void`.

Of course, if this was an actual call AST, some asserts would definitely trigger, because we're sending an `&IObs<send$T>` into a parameter expecting a `&IObs<Opt<dis$X>>`, and the other argument doesn't match either.

This is an unusual call. It's manufacturing a `dis$X` out of thin air. The abstract function doesn't have that, and has no idea where it comes from, while it's in the typing pass. It's up to the instantiator to substitute `send$T` and `dis$X` correctly so that the arguments and parameters line up.


### Monomorphizing

Let's walk through some monomorphizing cases.

Someone calls `send<Opt<str>>(&IObs<Opt<str>>, Opt<str>)`. The call's instantiation bounds say that `D` = `func drop(Opt<str>)void`.

We look through the edges to find all the structs that could implement `IObs<Opt<bool>>`.

  * We consider an instantiation `OtherStruct<bool>` impls `IObs<bool>` from an `impl<E> OtherStruct<E> for IObs<bool>`.
     * Its interface doesn't match, abort.
  * We consider an instantiation `MyStruct<int, Opt<str>>` impls `IObs<Opt<str>>`, from our original impl.
     * Its interface matches, so proceed.

We look at that edge's full name and find its ZZ = `int` and X = `str`.

Now we look at the call from the typing pass: `send<send$T>` calls `dis<dis$X>(&IObs<Opt<dis$X>>, Opt<dis$X>)` with instantiation bounds `D` = `func drop(send$T)void`).

We have all the substitutions we need.
 
 * We know from the original call's name that `send$T` is an `Opt<str>`.
 * We know `dis$X` should be `str` from the edge.

The call becomes: `dis<str>(&IObs<Opt<str>>, Opt<str>)` with instantiation bounds `D` = `func drop(Opt<str>)void`).



### Translate Impl Bound Argument Names For Case (TIBANFC)

When we're compiling the override dispatcher for func len, specifically for its case for MySome:

`odis{impl:98}<^len.odis{impl:98}$0>(&MyOption<^len.odis{impl:98}$0>).case`

we have a Monomorphizer for it that contains the actual generic arguments and the bound arguments.

It accidentally contained:

`MySome.bound:drop:66<>(^impl:98$0) -> drop(int)`

because we just copied those arguments over from the impl's bound arguments. However, the case then tries to resolve the MySome<$0>, or more specifically:

`MySome.bound:drop:66<>(^len.odis{impl:98}$0)`

which isn't in the map.

You can see the problem: when we brought the impl bound args over, they were keyed with full names that had impl placeholders. In other words, these bounds are still phrased in terms of the impl.

So, when we bring impl bounds over, we need to translate those full names.



# Must Declare All Type Outer Envs First (MDATOEF)

We used to declare a type's outer environment when we compiled its definition. However, this exposed a problem when this sequence of events happens:

 * We haven't yet compiled struct `LocationHasher`
 * We start compiling struct `Level`.
 * We're checking the template instantiations for `Level`'s member `HashMap<K, V, H, E>`
 * We're checking the template instantiations for that `HashMap<Location, Tile, LocationHasher, LocationEquator>`'s bound function `__call(&LocationHasher, Location)int`.
 * We're looking for any potential function matching that signature.
 * We're looking up the environments for all args, in this case `LocationHasher` and `Location`.
 * In looking up the environment for `LocationHasher`, we trip an assertion because *we haven't yet compiled* `LocationHasher` so its environment hasnt yet been declared.


So, the solution we chose is to declare the outer environment in structs' pre-compile stage.



# Not sure if these are true

## Some Rules are Hoisted Out of Default Param (SRHODP)

This snippet is using a **bound function**:

```
func launch<X, func foo(int)void>(x X)
where func foo(int)void {
  ...
}
```

It's particularly nice because the call-site can hand in something that's not called `foo`.

This `func foo(int)void` generates three rules (see SROACSD):

 * DefinitionFunc, used when compiling definition, creates a prototype that can later be called.
 * Resolve, which looks in the current environment for a matching function.
 * CallSiteFunc, used when compiling call site, which makes sure the given prototype has the right parameters and returns.

If the call-site wants to pass in their own prototype, then they *don't* want that Resolve rule in there. So, we need Resolve only be a *default* expression, only run when the user doesn't specify something.

But we don't want the other two rules (DefinitionFunc, CallSiteFunc) to be defaults, we want those to always be there. So, we'll hoist them out of the generic parameter's "default rules" and into the function's main rules.


## Default Parameters Can Only Depend on Other Default Parameters (DPCODODP)

We had:

```
struct Functor1<F Prot = func(P1)R> imm
where P1 Ref, R Ref { }
```

But when defining it it had no idea what to do. It should have generated a DefinitionCallSR which would produce the right prototype, but it didn't know what coords to use for its param and return.

I believe this means that we should have had some placeholders for the P1 and R.

That then means that P1 and R should have been generic params themselves.

So, it should be like this:

```
struct Functor1<P1 Ref, R Ref, F Prot = func(P1)R> imm { }
```

And then we should make placeholders for P1 and R, and let the 3rd param's DefinitionCallSR create a prototype using those two. Then things would work.



## Only See Direct Caller's Environment (OSDCE)

Note from later: i think this might be obsolete. i believe we just tiebreak them.

Let's say we have these definitions:

```
interface MyInterface<T>
where func drop(T)void { }

struct MyStruct<T>
where func drop(T)void { ... }

impl MyInterface<T> for MyStruct<T>
where func drop(T)void;
```

Which expands to something like this:

```
#!DeriveInterfaceDrop
interface MyInterface<T>
where func drop(T)void { }

virtual func drop(self MyInterface<T>);

#!DeriveStructDrop
struct MyStruct<T>
where func drop(T)void { ... }

func drop(self MyInterface<T>);

impl MyInterface<T> for MyStruct<T>
where func drop(T)void;
```

This tree of steps happens when we compile the itables:

 * We're looking for all overrides for the abstract function `func drop(self MyInterface<T>)void`.
    * We use the environment from it, **which includes a conjured** `where func drop(T)void` bound.
    * We use a placeholder for `T` (named `drop(MyInterface<T>).$0`, but we'll keep calling it `T`).
    * We see that `MyStruct` implements it, so we try resolving a function `func drop(MyStruct<T>)void`.
       * During solving, we conjure a `MyStruct<T>` and postpone its resolving.
       * We conjure an environment with the conclusions from the solving, **including the conjured** `func drop(MyStruct<T>)void`.
       * Now after solving, we're actually resolving that `MyStruct<T>`.
          * During solving, we conjure a `func drop(T)void` and postpone its resolving.
          * Afer solving, we actually want to resolve that `func drop(T)void`.
             * Uh oh! We find **two** matching prototypes.

It seems that both of them are conjuring a prototype to use and requiring things.

So, we don't send anything downward.


# Require Rune for Function Bound?

In this example:

```
func moo(i int, b bool) str { return "hello"; }

exported func main() str
where func moo(int, bool)str
{
  return moo(5, true);
}
```

It's ambiguous which moo we're referring to. Which one should we use?

We could say that since it's ambiguous, they should stuff it into a rune and then call the rune directly... but this feels like it would be fragile.


# Macro-Derived Sibling Functions Often Need All Rules From Original (MDSFONARFO)

Macros can take a denizen and generate new denizens right next to them.

For example, in:

```
sealed interface Opt<T Ref>
where func drop(T)void
{ }

struct Some<T>
where func drop(T)void
{ x T; }

impl<T> Opt<T> for Some<T>
where func drop(T)void;
```

The implicit InterfaceDropMacro defines a drop function for that interface.

It almost looks like this:

```
func drop(this Opt<T>) void {
  [x] = this;
}
```

But wait! That doesn't work! There needs to be a `<T>` parameter and there needs to exist a drop for that T, like:

```
func drop<T>(this Opt<T>) void
where func drop(T)void {
  [x] = this;
}
```

We can see now that this `drop` function actually takes a _lot_ from the original interface.

It needs:

 * Generic parameters
 * Concept functions
 * Rune types

Pretty much everything.


# Resolving Races Between Fallback Strategies (RRBFS)

When a solver can't figure out some things, then it uses SMCMST/CSALR to throw a hail mary and guess the best type to use for a call's generic parameter. It only happens after the regular solve has hit a dead end.

The other thing we do when a regular solve has hit a dead end is to add the next identifying rune's placeholder, when solving the definition.

We had a bug (search for test case with RRBFS) where the hail mary was thrown even when solving a definition.

Moral of the story: these two last-resort strategies can sometimes race.

For now, we resolve it by only doing the hail mary for call sites.
