
(See also [InstantiatorRegions.md](InstantiatorRegions.md))

# Basic Regions (BASREG)

## The Challenge

The challenge is that we can turn a region immutable at will. In all these below cases, `arr` is type `main' []Ship` and `main'` is mutable, but once we do anything pure (pure block, parallel foreach, pure call) that `main'` becomes immutable.

Case 1: A pure block

```
func main() {
  arr = [](10, i => Ship(i));
  pure {
    foreach ship in arr {
      println(ship.fuel);
    }
  }
}
```

Case 2: A parallel foreach

```
func main() {
  arr = [](10, i => Ship(i));
  parallel foreach ship in arr {
    println(ship.fuel);
  }
}
```

Case 3: Calling a pure function

```
pure func printAllShips<r' ro>(arr r' []Ship) {
  foreach ship in arr {
    println(ship.fuel);
  }
}
func main() {
  arr = [](10, i => Ship(i));
  printAllShips(&arr);
}
```

Case 1 and 2 are actually pretty similar. Case 2 can probably even use case 1 under the hood.

So how do we know that a region is immutable? It would be pretty difficult to track that in the coord, because as we cross that `pure {` boundary wed' have to change the coord of everything coming in via environment and stuff.


## A: Re-declare regions in a more local environment

We can have the region itself in the environment. It's name would be `main'` and its value would be something like `Region(mutable)`.

When we get to the `pure {` we'll redeclare those regions, so it would be `main'` -> `Region(immutable)` at that point.


## B: Compare "stack heights" to know whether somethings' immutable

Lets' say that `main'` is at height 0 and that `pure {` is at "height" 4, and inside it is height 5. While were' inside it, we know that `main'`s' height is below `pure {`s' height, and therefore anything in `main'` is immutable right now.


## Go with option A

Option A seems a little more precise. Option B indiscriminately turns everything immutable, which works well for these examples, but maybe one day we'll have a `xrw` mutex open and we want it to remain mutable even inside the pure block. Option A can do that, option B cant'.

So, every coord will have the name of a region that lives in the environment.


# Overload Resolution With Regions (ORWR)

Every coord has a region ID in it. During solving, we actually won't care about whether a region is mutable or immutable, we'll just figure out which caller regions send into which callee regions.

After the overloading is done, probably near the instantiation checking, we'll also check that the regions line up well.


# Do We Have Region in Coord? (DWHRC)

The user will think of the coord as having a region. They'll be using notation like `a'Ship` and `b'&Ship` which does seem to imply it's in the coord.


However, it doesn't necessarily have to be that way under the hood, it could just be a generic param on the ship, like `Ship<a'>` and `&Ship<a'>`. It would be pretty similar conceptually, really.


For now, we'll actually have it in both places: `a'Ship<a'>` and `b'&Ship<b'>`. The one in the coord will really just be derived from the generic parameter.


One day, we might decide which makes more sense. This section covers how both would work.


## Have Region Only In Coord (HeROIC)

(This is not the case yet, this is a hypothetical.)

If we do this, then this struct:

```
struct Ship {
  engine Engine;
}
```

would be this under the hood:

```
struct Ship self'{
  engine self'Engine;
}
```

The `self'` in `self'{` is just a way for us to designate that the self region is referred to by "`self'`". We could also just make `self'` a special reserved region keyword.


### Transforming Regions When Getting Members (TRWGM)

When we're in a function and we try to grab a member, like the `s.engine` here:

```
func main() {
  s = Ship(Engine(7));
  e = s.engine;
  println(e);
}
```

We actually need to translate that Coord(`own`, `Ship.self'`, `Engine`) to Coord(`borrow`, `main.self'`, `Engine`).


We do something exactly like this when we grab something of type `X` out of a `MyStruct<X>` like `MyStruct<int>`; we translate that `X` placeholder to `int` when we bring it into the user's world.


To do this, we can either:

 * Look up the name of the region from inside the struct.
 * Make it deterministic, so we can always determine a struct's default region id.

We go with the latter; the function TemplataCompiler.getDefaultRegionId will tell us the region id for any template. Search TRWGM for some instances of this.



## Have Region As Only Generic Parameter (HRAOGP)

(This is not the case yet, this is a hypothetical.)

Under the hood, our Coord can still just contain an ownership and a kind.

It would actually work well for things like `int`, `bool`, `float`, `void`, `never`, because they dont really have regions and they kind of transcend this whole region _thing_.

Under the hood, structs will have a region generic param. This struct:

```
struct Ship {
  engine Engine;
}
```

would really be this under the hood:

```
struct Ship<a' own> a'{
  engine Engine<a'>;
}
```

The `own` in `a' own` is needed... i'm not sure why. Maybe we don't need it. Maybe we do?

 * Maybe because we can't just own something in any ol' region. If we could create something in any ol' region, then if we have a `MyStruct<a', b'>` it's hard to know whether we can blast it away. Would we indirectly be blasting away something in `b'`?

The `a'{`. That is a way to give a name to the region that this struct is in.

The `a'` in `Engine<a'>` is (conceptually) needed because if we require a parameter, they'll have a parameter.


The advantage here is that it makes it a bit easier to do substitutions.


## Start Using Internal Onion Typing (SUIOT)

We don't really need a default region if instead of coords we have just... types. Like:

 * int
 * bool
 * MyStruct\<int\>
 * Heap(MyStruct\<int\>)
 * Borrow(MyStruct\<int\>)

Kind of like you would see in languages that allow any number of nested pointers/references.

In this approach, we wouldn't actually need to have a special region at all. This struct:

```
struct Ship {
  engine Engine;
}
```

has no place that we need to specify a region for anything.


# Implicit Default Region

Every denizen has a self region.

For example, this `main` has a default region:

```
func main(args []str) {
  list = List<int>(0);
  println(list.len());
}
```

Here's the same exact thing with all the implicit things added:

```
func main<m' rw>(args m'[]str) m'{
  list = m'List<int>(0);
  println(list.len());
}
```

Here's a struct:

```
struct HashMap<K, V> {
  size int;
  arr []HashMapNode<K, V>;
}
```

and here it is with the implicit region added in:

```
struct HashMap<K, V, x'> x'{
  size x'int;
  arr x'[]HashMapNode<K, V>;
}
```

It seems this serves two purposes:

 * To answer the question "what region is `arr` in?"
 * To answer the question "what region is the `HashMap` in?". though, thats not really a question the HashMap ever really needs to know...

it does also make the question awkward of... if we leave a region annotation off, what region is it? well, i guess its the default region. kind of makes sense.

does a struct really need to know its own region?


having it as a struct parameter also lets us have types that are region-less... ints, floats, etc. but that kind of leaves behind things like Vec3 which want to be inline values too.


heres a weird question... if it has a value, then what would we hand in as its initial one? like, what would the monomorphizer hand in for `main`s region?
well... hmm. "root mutable"?
i guess thats kind of the same thing as having a function define its own ambient region?


 * can the thing's region change?
    * yes, yes it can actually. the region varies, independently of the type. but we could do that with generic params too i think.
    * it cannot be moved or changed if it's part of of an immutable one.
    * its special because this particular region will be made immutable by a pure function, and everything indirectly owned, but not necessarily the other regions.
    * so it's special, we know that. but does that mean it should be a generic parameter?
    * are we able to turn an xrw region into an immutable? i think so. so any of these can vary.
 * its special because its the region of all the owning things inside it. is there a difference there?

a function can independently move things around. it has a different region than the things it contains. structs kind of are decoupled too, in the opposite direction.

It's not entirely clear whether this is needed. We'll see if it comes into play. I wonder if this is kind of like the region of the stack frame?


## In Functions, Are Regions Actually Generic Parameters? (IFARAGP)


Take this function:

```
pure func len<K Ref imm, V, H, E, r' imm>(
  self r' &RHashMap<K, V, H, E>)
int {
  return self.size;
}
```

`r'` is actually a generic parameter.


This is particularly beneficial because it will help us reuse the substitution logic. For example, if we have this struct (s' region added for clarity):

```
struct HashMap<K Ref imm, V, H, E, s'> where ... s' {
  hasher H;
  equator E;
  size s'int;
  arr s'[]HashMapNode<K, V>;
}
```

then the caller can just hand in the `s'` and it will affect the `size` and `arr` inside.



It also makes overload resolution easier.

Let's say we have a function taking a region param:

```
func myFunc<r'>(x &r'MyStruct) { ... }
```

and we call it from foo:

```
func foo() {
  a = MyStruct(...);
  myFunc(&a);
}
```

`foo` isn't just handing in a `&MyStruct`, it's actually handing in a `&foo'MyStruct`. But when we hand it into the overload resolution, it will have a conflict because of course, `r'` isn't `foo'`.

But it does work if we think of `r'` as a generic parameter, a rune. In that case, `r'` could totally be `foo'`.


## Default Region is a Generic Parameter (DRIAGP)

From IFARAGP, we saw that every region is a generic parameter, because overload resolution needs to equate the incoming `foo'` to the receiving `r'`.


This is also the case with the implicit default region (`moo'`), because `bork<bork', z'>` might be called from `func moo<moo', a'>` like `bork<a', moo'>`. We need to have `bork'` as a generic parameter so that where `moo` calls `bork`, it can specify what region (`a'`) is fed in as `bork`'s default region. But... this is theoretically possible if its not a region generic parameter, it's just specified in the coord instead.


Is there any unique benefit to having it as the coord?
Perhaps that it can be transmuted easily?
For example, when we convert a `bump'Thing<myimm'whatever>` from a struct containing raw refs to one containing real refs... `x'Thing<myimm'whatever>`. theyre legit different. they contain different data. theres no such thing as just casting them.

We *could* think of them as the same data, just that the generations are nulled out beforehand and then populated afterward...


a regular struct pointing at things in its own region will always be full generational refs.
a struct, generic, pointing at things in a different immutable region will have raw refs.
a struct, generic, pointing at things in a different *mutable* region will have gen refs.

it really doesnt matter what region the struct is. its contents depends on whether it points at things in a different region.

interestingly, that might change depending on the containing function.

take `pure func find<T>(self &List<T>, thing &T)`.
expand to `pure func find<t', T, me'>(self &me'List<t'T>, thing &t'T) me'Opt<&t'T>`

if t' is mutable, then that `me'Opt<&t'T>` will contain a gen reference.
if t' is immutable, then that `me'Opt<&t'T>` will contain a raw reference.

t' starts immutable. we're in a pure function after all.

but then, that `t'` becomes mutable again, and we need to fill in those references. so it flips to the other one.

the things that come out of a pure function are guaranteed to be iso, so we _can_ copy, if we want... we'd have to map all contained references.

what if theyre in an arena? we'll need to copy em out anyway.
actually, maybe not. we know whether the callee region is in a specific custom region. if it is we copy. if we handed in our own, we just do the refreshing of the generations.
we could do a runtime test. if theyre different, but theyre both malloc, we can call mimalloc merge or something.

as tricky as this is, i think this is actually orthogonal to whether its considered a generic param or not. if it did affect things, it would be that we can transmute from contains-raw to contains-gen. that suggests theyre the same type in the end. but thats an optional optimization, kind of. should it really dictate how the frontend works?

but perhaps we can just _always_ type-erase regions away in structs. seems like they should always have generations.

if we're going to be type-erasing/merging away the differences between instantiations with different regions, then this line of thinking is moot. we still dont really know whether to have it as a generic parameter.

lets just have it for now.





Whatever we do, we should do it also for functions that _only_ have a default region, so that we're consistent.


This is also why we need generics. Since its a generic parameter, the old system would have monomorphized every function according to every root (exported, iow) function.


## Cant Specify Region as Generic Arg (CSRGA)

No particularly strong reason for this, it just seems best to hide DRIAGP from the user.


## Default Region is the Only Implicit Generic Parameter (DROIGP)

When we call a struct template and give it args, like `Map<int, str>`, one would think that `int` and `str` are the only arguments we're handing in. There's one hidden one, the struct's region.


## HigherTyping Explicifies Default Region for Kind Calls (HTEDRKC)

can we figure out at highertyping time whether we'll hand in a context region to a given CallSR?

yes for types. not for functions.

functions have overloads, so we're never sure if we've already handed in the region or not, we need to know the receiving function's type, which we don't know until overload resolutions which happens in the typing phase.

but for structs and interfaces, we can know, because there's no overloading for those.

actually wait, we couldnt specify the default region via parameter if we _tried_. so maybe it's fine to add it in. the only oddity is making sure it's the last parameter, coming even after all the default generic parameters.


# Every Function Receives a Mutable Region (EFRMR)

Also known as the default region.

Pure functions just receive one that is guaranteed to contain none of the parameters.

When someone names their body's default region, they're really just naming the incoming mutable region.

Every function receives a mutable region, so we don't even really need to mention it in the signature. 

Unless... we want to give the caller control over which region to use.

Interface method: I guess in that case they'll hand that in as a particular region parameter, and then it's up to the implementation/override to switch to using that as their default region.


# Old Thoughts

(Not relevant any more)

Even though regions appear as generic parameters, they arent' really.

Take this function:

```
pure func len<r' imm, K Ref imm, V, H, E>(
  self r' &RHashMap<K, V, H, E>)
int {
  return self.size;
}
```

It's actually more like...

```
pure func len[r' imm]<K Ref imm, V, H, E>(
  self r' &RHashMap<K, V, H, E>)
int {
  return self.size;
}
```

Just dont' think of them as generic params. Think of them more like permissions (like c++ const) which are tied to the specific function. It's like the function has its own permissions.


# Returning Directly Into A Region (RDIAR)

Take this clone function:

```
func clone<E>(list &List<E>) List<E>
where func clone(&E)E {
  return List<E>(Array<mut, E>(list.len(), { list.get(_).clone() }));
}
```

If were' to regionize it, its' unclear how wed' handle that returned `List<E>`.

We have a few options:

 * Require any returned value to be an iso, like `iso List<E>`.
 * Have it be part of `clone`s' own region, and then implicitly copy/transmigrate it at the end.
 * Take the "output region" in as a region parameter.

That third one is the most promising. An example:

```
pure func clone<'o, 'l, E>(list l' &List<E>) o' List<E>
where pure func clone<'e, o'>(e' &E) o' E c' {
  return o' List<E>(Array<mut, E>(list.len(), { list.get(_).clone() }));
}
```

Were' taking the output region in as a parameter (`o'`). Now, the caller can specify where they want it to go. This means they can put it into a new region of their choice and then merge it in later.


# Inferring Regions For Pure Functions (IRFPF)

Take this clone function:

```
pure func clone<'o, 'l, E>(list l' &List<E>) o' List<E>
where pure func clone<'e, o'>(e' &E) o' E c' {
  return o' List<E>(Array<mut, E>(list.len(), { list.get(_).clone() }));
}
```

A lot of functions kind of look like this, where they have a region for every parameter (minus perhaps the primitives) and another region for the return.

Lets' make that automatic for pure functions. It then becomes:

```
pure func clone<E>(list &List<E>) List<E>
where pure func clone(&E)E {
  return List<E>(Array<mut, E>(list.len(), { list.get(_).clone() }));
}
```

We might need some annotations if we want to return a reference to one of the parameters, or something inside them that isnt' a generic parameter.


# Concept Functions Can Have Regions (CFCHR)

Take this struct:

```
#!DeriveStructDrop
struct RHashMap<K Ref imm, V, H, E>
where func(&H, &K)int,
    func(&E, &K, &K)bool
{
  hasher H;
  equator E;
  table! Array<mut, Opt<RHashMapNode<K, V>>>;
  size! int;
}
```

Note how it's just using the regions as known by its parent function. That's totally fine. And if we wanted to manually specify one of the parent function's regions in there, we could.

One day with full regions it could look like:

```
#!DeriveStructDrop
struct RHashMap<K Ref imm, V, H, E>
where pure func<x'>(x' &H, x' &K)int,
    pure func<x'>(x' &E, x' &K, x' &K)bool
{
  hasher H;
  equator E;
  table! Array<mut, Opt<RHashMapNode<K, V>>>;
  size! int;
}
```

But we don't wanna do that quite so soon, it's not really needed.

(When we change that, look for all occurrences of CFCHR.)


# Specifying Regions In Expressions (SRIE)

Lets' say we have this ship:

```
struct Ship { fuel int; }
```

Making a new region:

```
region x';
```

Putting something into that new region:

```
list = x'List<x'Ship>(&myArr);
```

(Let's say `myArr` is in `g'`)

Notes:

 * The `x'` in front won't affect any of the arguments for now. It's an arbitrary decision, we can change it later.
 * The `x'` in the `x'Ship` is needed for now. Well' see if we can infer it later.


The `x'` in front is actually specifying the output region. Without it, it would hand in the functions' default region. The compiler will figure out what the callee is calling their output region, and make sure we pass it in as that particular generic arg.

So if we have a callee:

```
pure func List<T, 'r, o'>(arr r'&[]T) o'List<T> { ... }
```

Then as we call it, we'll send in the callers' `x'` in and equate it with the callees' `o'`.

The tentative convention is to have that be the last generic parameter (note how the generic params has the `r'` before the `o'`) because we have this nice alternate way to specify it.

We could also specify it manually if we wanted to do that instead:

```
list = List<a'Ship, 'g, a'>(&myArr);
```

but it's rather tedious as we had to specify that second generic parameter (`g'`) along the way to get to that last one.


# Looking Up Coords From Outside (LUCFO)

The LookupSR rule usually looks up things like struct generics like `MyStruct<Ship>` or const generic things like integers and booleans. Theoretically coords too, for example if we typedef `&Ship` to `ShipRef` or something.

However, if we look up a coord, we'll be bringing in that coord's region, which likely doesnt make sense in the context of this function.

Two options:

 * Don't allow looking up coords from outside.
 * Assume they only have one region: the default one. Map that to the default region of where the LookupSR is.

Let's do the latter. Note that itll require LookupSRs to know what region they're loading into.


# Lookups Need To Know Their Region (LNTKTR)

Let's say we have a `MyStruct<T Ref>` and some other function `foo` mentions a `MyStruct<OtherStruct<Ship>>`. In post-parsing, `foo` doesn't yet know that the `T` is a Ref, so it doesn't know whether `OtherStruct<Ship>`'s kind will be coerced to a coord. That information comes later, in the higher typing or typing pass.

When we get there, if `T` is indeed a coord (which it is here), the higher typing or typing pass will need to know what region to bring it into. But how?

Two options:

 * Know about the ambient/default region in the higher typing / typing pass.
 * Include that information in the CallSR (or LookupSR in the case of looking up a kind).

We'll go with the latter, because we're trying to make all the regions very explicit and not ambient in the later phases.

It alos helps with LUCFO.

## Ignore Call and Lookup Regions in Typing Pass (ICLRTP)

This _does_ make CallSR and LookupSR a little awkward, because those classes are reused in later stages and they still remember their region that late. This might be a reason to make a strongly-typed rule AST again (see STRAST).


# Regions Apply Deeply To Generic Call Args (RADTGCA)

If we have a generic call with a region like `x'List<Ship>`, then that `x'` applies deeply, as if we wrote `x'List<x'Ship>`.

Whenever we're doing post-parsing to figure out the region for everything, we keep track of the nearest containing region, like the `x'` above. This is known as the "context region" there.



# Making New Regions For Generic Coords (MNRFGC)

When we have a generic coord, like `func tork<K, V>(self &Splork<K, V>)`, that coord needs a region.

There are two options:

 * Assume it's in the default region (tork').
 * Associate a separate generic region for it.

The first one is rather restricting, and means that by default we won't be able to give separate-regioned data to it.

The second one means that we can't hand them to functions that expect objects of the same region, like `where func bork(k Ship, v Missile)` does. However, we can't call that anyway; the only functions we can hand a `T` to are in the form of function bounds, like `where func bork(k K, v V)`. We can call that with `K` and `V` no matter what regions they're in, and it's up to the caller to figure out what `K` and `V` and `bork` are and whether `K` and `V` are in the same region and whether `bork` expects things of the same region. In other words, the concern of whether things are in the same region is completely irrelevant to `tork`'s definition.

So, there are no downsides to the second one. We'll go with that.

An interesting question then arises: if we have an incoming coord `T`, what region is it in?


### A: Irrelevant

It's irrelevant. If we have a PlaceholderTemplata(, Coord), then there's no need (or way) to specify a region there.

However, with this approach, we then can't represent `&T`. And if we try, and put it into a coord, what region does it get?


### B: Implicit Separate Region Generic Param

Create an implicit region generic param: `func tork<K', V', K'K, V'V>(self &Splork<K, V>)`.

We tried this for a bit, but it led to some complications. For example, if we had an interface:

```
interface IFoo<T> {
  func zork(self, val T);
  func kork(self, val T);
}
```

then the anonymous substruct became:

```
struct IFooAnonymousSubstruct<T'T, Z'Z, K'K, T', Z', K'> {
  zork Z;
  kork K;
}
```

and it seemed rather complex to know what order those generic params should come in. See IRRAE, though it didn't address anonymous substructs.


#### Implicit Region Runes are Added to the End (IRRAE)

(This is obsolete, we now have implicit region variables, not implicit region params)

We add a new region rune because of MNRFGC.

For example, `func moo<T, Y>(a T, b Y)` might become. `func moo<t'T, y'Y, t', y'>(a T, b Y)`

We have three options for where to put that region param in the list:

 1. Like the above, add them all to the end.
 2. Add it to the beginning, like `func moo<t', y', t'T, y'Y>`. Rust does this.
 3. Add each before its coord rune: `func moo<t', t'T, y', y'Y>`.
 4. Add each after its coord rune: `func moo<t'T, t', y'Y, y'>`.

2, 3, and 4 require that if we say something like `moo<int, bool>` then we do some fanciness to figure out that the caller is trying to specify something other than generic arguments 0 and 1.

The only one that acts intuitively is option 1. If a user specifies `moo<int, bool>` then the arguments they think they're specifying line up with the definition's actual generic parameters.

So, we'll go with option 1.

That also makes the implementation easier, nice bonus.


### C: Implicit Region Variable

We can create an implicit region variable: `func tork<K'K, V'V>(self &Splork<K, V>) where K' Region, V' Region`.

It does mean that we have to have some special logic when we fill in placeholders; when we want to make a placeholder for a coord, we'll make a placeholder for its region too.


### D: Treat Like Kind

This is inspired by (and possibly equivalent to?) option C.

Recall how when we want to make a placeholder for a coord, we actually just make a placeholder for the underlying kind, and conjure up an own coord to it.

When we want to make a placeholder for a kind, we can similarly make a placeholder for the underlying region as well.


### Conclusion

We'll go with D for now.



# Implicit Region Runes are Added to the End (IRRAE)

(This is obsolete now, we dont add implicit region runes)

We add a new region rune because of MNRFGC.

For example, `func moo<T, Y>(a T, b Y)` might become. `func moo<t'T, y'Y, t', y'>(a T, b Y)`

We have three options for where to put that region param in the list:

 1. Like the above, add them all to the end.
 2. Add it to the beginning, like `func moo<t', y', t'T, y'Y>`. Rust does this.
 3. Add each before its coord rune: `func moo<t', t'T, y', y'Y>`.
 4. Add each after its coord rune: `func moo<t'T, t', y'Y, y'>`.

2, 3, and 4 require that if we say something like `moo<int, bool>` then we do s
ome fanciness to figure out that the caller is trying to specify something other
 than generic arguments 0 and 1.

The only one that acts intuitively is option 1. If a user specifies `moo<int, b`

For example, `func moo<T, Y>(a T, b Y)` might become. `func moo<t'T, y'Y, t', y'>(a T, b Y)`

We have four options for where to put that region param in the list:

 1. Like the above, add them all to the end.
 2. Add it to the beginning, like `func moo<t', y', t'T, y'Y>`. Rust does this.
 3. Add each before its coord rune: `func moo<t', t'T, y', y'Y>`.
 4. Add each after its coord rune: `func moo<t'T, t', y'Y, y'>`.

2, 3, and 4 require that if we say something like `moo<int, bool>` then we do s
ome fanciness to figure out that the caller is trying to specify something other
 than generic arguments 0 and 1.

The only one that acts intuitively is option 1. If a user specifies `moo<int, bool>` then the arguments they think they're specifying line up with the definition's actual generic parameters.

So, we'll go with option 1.

That also makes the implementation easier, nice bonus.


# Regions Exponential Code Size Explosion, and Mitigations (RECSEM)

As part of MNRFGC, we're making a lot of implicit readonly regions. This could mean a bit more codesize, as we stamp every function is stamped 2^R times, where R is the number of readonly regions.

This is particularly a problem because we monomorphize interface methods eagerly. `abstract func york<K', V', K'K, V'V>(self &Splork<K, V>) where func bork(k K, v V)`, might eagerly monomorphize mutabilities for `K'` and `V'`.

Fortunately, this isn't quite a codesize _explosion_. Even if we have a complex function where R=4, it'll be stamped 16 times, but if it only calls `mork<M>(M)` and `gork<G>(G)`, it's not like `mork` and `gork` are each stamped 16 times. Nay, they're stamped twice each. So really, this 2^R is only a local thing, because at the instantiator stage, a region is only a boolean.

More fortunately, many of these monomorphs also happen to be identical after stamping. Why? Because the main difference between an immutable monomorph and a mutable monomorph is in how they dereference the `T`. And `T`s can never be dereferenced! They're generic. Though, this only holds if there are no function bounds (or trait bounds) like `func zork<K, V>(self &Splork<K, V>) where func bork(k K, v V)`. `zork`'s caller might be an immutable monomorph that expects `zork` to call an immutable monomorph of `bork`. Though, when that happens, we only monomorphize 2^Cr, where Cr = number of regions we're called with.

So the problem seems to be in this situation: an abstract function that has generics and function bounds.


Perhaps if we need to reduce codesize, we _could_ use polymorph tricks, handing in a function pointer or an entire vtable pointer.


# Specify Regions in Post Parsing or Typing (SRPPT)

We had a choice, between an ExpressionCompiler.evaluate parameter named contextRegion, or have the region explicitly specified in every expression given to the typing pass.

### Every AST Node Specifies its Result Region

Every AST node, like ConstantIntSE, CallSE, etc. will specify its region.

We'll probably fill this in in the post-parsing stage, before it's blasted apart into rules and we lose the hierarchy.

Possible weakness: We might want to infer different regions depending on the type, which only the typing pass has. For example, if it's a migratory data, like some sort of atomically refcounted thing, we might want it in the atomically refcounted region. Same with primitives like integers. I suppose if we really need this, we can ignore the specified region for that type. Or just allow casting between them or something.

### Rejected Alternative: Context Region Parameter

To the user, there's the notion of a context region, a region that is applied to any following expressions. The typing pass can also think of it this way, such as by ExpressionCompiler.evaluate() taking in a parameter named contextRegion.

When evalute() sees an expression, its result will by default be of the contextRegion.

To make a result in a different region, we'd use a specific AST node (AugmentSE) and it would change the contextRegion to that region.

Note from later: This actually falls apart because templexes' hierarchy is lost when transformed into rules.



# Simplifying Regions in Monomorphizer

We do regions like any other template parameter. blah blah

we hand them in like template parameters, and the solver figures them out like template parameters

when they finally get to the monomorphizer, it does an interesting thing: it completely ignores the differences between any of the regions. all it does it pay attention to their mutabilities.

So these all lower to the same thing:

 * `MyThing<'a, 'b, bool>` if `a` is mut and `b` is mut
 * `MyThing<'p, 'q, bool>` if `p` is mut and `q` is mut
 * `MyThing<'x, 'y, bool>` if `x` is mut and `y` is mut

and all these are the same:

 * `MyThing<'a, 'b, bool>` if `a` is imm and `b` is mut
 * `MyThing<'p, 'q, bool>` if `p` is imm and `q` is mut
 * `MyThing<'x, 'y, bool>` if `x` is imm and `y` is mut

So in the monomorphizer, its' kind of like were' actually invoking with mutabilities, not with the actual regions' identities.



# Typing Phase

Typing phase's RegionPlaceholders contain:

 * introducedLocation, which has the stack height of when it was introduced.
 * initiallyMutable boolean

We'll also track the stack height of the latest pure block.

If we want to know if a region's immutable, check if the latest pure block is more recent than the region's introducedLocation. Or, if its initiallyMutable is false, it's definitely still immutable.

This is nice, because a variable's type doesn't change halfway through a function which would confuse the backend.


# Pure Should Be Outside the Block (PSBOB)

We had this code:

```
exported func main(s &Spaceship) int {
  pure block {
    x = s.engine;
    y = x.fuel;
    y
  }
}
```

It resulted in a BlockTE containing a PureTE which contained some lets and dots.

However, we ran into some awkwardness. The lets inside the pure block were being unstackified outside the pure block.

So instead, let's make it so the pure is outside the block.



# Regions and Externs

An extern call will implicitly try to copy all shared values into the native realm.

Sending any owned values into the outside world isn't really supported yet in a regions world.

It does this by compiling the `extern` statement assuming that all regions are `ext'`, the extern function's default region. The ExternFunctionCall AST node will do the proper transmigration for these values into native memory.


# Primitives Automatically Transmigrated to Default Region (PATDR)



# Need Pure Function Call AST Node (NPFCASTN)

We originally had only a PureTE instruction, which reinterpreted everything outside of it as immutable. This snippet produced one:

```
pure func pureFunc<r'>(s r'str) bool {
  true
}
exported func main() bool {
  s = "abc";
  return pureFunc(s);
}
```

It produced a PureTE(FunctionCallTE(LocalLoad(s))) and that worked well.

However, that doesn't work for examples like this:

```
pure func pureFunc<r'>(s r'str) bool {
  true
}
exported func main() bool {
  return pureFunc("abc");
}
```

It would produce a PureTE(FunctionCallTE(StrTE("abc"))), but that StrTE isn't outside the pure, so it doesn't get regarded as immutable when it should.

Instead, we need to evaluate the arguments first:
[ "abc" ]
and then do a "pure call" of each argument immutabilified.


For this, we have some extra information in FunctionCallTE, and also the instantiator produces ImmutabilifyIE's around the arguments.


WAIT

this doesnt seem right. we could call a function bound that just *happens* to be pure. that means that this logic should really just go in the instantiator. in a way, the pureness is a callee implementation detail (which we then bring to the call-site as optimization). we should take pure function calls out of the templar.


# Pure Merging Happens Before Rest of Solving (PMHBRS)

NOTE FROM LATER: This is largely fubar, pure merging kind of fell apart.

This is a basic function that does pure merging:

```
struct Ship { fuel int; }
pure func zork<m' imm>(a &m'Ship, b &m'Ship) int { 42 }
pure func bork<r' imm>(x &r'Ship) int {
  return zork(x, &Ship(28));
}
exported func main() int {
  return bork(&Ship(42));
}
```

When bork calls zork, it merges bork' and r' into zork's m'. This is similar to Rust's lifetime subtyping, where two callsite lifetimes can be merged into one intersection lifetime for the callee.

As far as we know, this can only be done for the parameter's outer region; we wouldn't be able to e.g. merge the `s'` in `Ship<s'>` (we *might* be able to merge them into an anonymous parameter `_'` rather than `s'`).

Without any special code for pure merging, we get an error like this: `Conflict, thought rune m was bork' but now concluding it's r'`

That's because the solver doesn't understand that, when convenient, it can decree two incoming regions to actually be the same region.

Since we only do merging for the parameters' outer regions, we can do this merging before the call solver starts.

#### Temporary solution

We track the outer region as part of ParameterS, and then do the merging in OverloadResolver.

Note that ParameterS's outerRegionRune might be the rune of a coord, from which the OverloadResolver should get the region rune.

Also, for now, when any merging is needed, we'll merge all incoming regions into one, even though that might not be sufficient for all/most functions.

This is just a temporary solution until we figure out something good to do.

Perhaps we could do something like a region-specific solver, that just solves the regions of things, kind of like we currently have the kind equivalency solver.

If whatever we do is expensive, perhaps we can only do it when we detect that the function receives different regions (the outer regions are different, not talking about the `T` in `List<T>`) or the arguments are different.



how do we deal with arrays?

[]Ship 

foo(myShipArr)

we know its fine if theyre all in the same region. This works:

```
struct Ship { fuel int; }

pure func zork<m' imm>(a &m'[]Ship, b &m'[]Ship) int { 42 }
pure func bork<r' imm>(x &r'[]Ship) int {
  return zork(x, &[]Ship(0));
}
exported func main() int {
  return bork(&[]Ship(0));
}
```

If we receive directly into `T`s, does it still work?

```
struct Ship { fuel int; }

pure func zork<m' imm, T>(a &m'T, b &m'T) int { 42 }

pure func bork<r' imm>(x &r'[]Ship) int {
  return zork(x, &[]Ship(0));
}
exported func main() int {
  return bork(&[]Ship(0));
}
```

It might work? It might think `T` is a `m'[]Ship`, which is fine.

the problem is with any denizen that's parameterized on a region. unfortunately, all generics are parameterized on regions.


But if we receive something parameterized on `T`, does it still work?

```
struct Ship { fuel int; }

pure func zork<m' imm, T>(a &m'[]T, b &m'[]T) int { 42 }

pure func bork<r' imm>(x &r'[]Ship) int {
  return zork(x, &[]Ship(0));
}
exported func main() int {
  return bork(&[]Ship(0));
}
```

whats T? we know it works if the T is in the same region as its parent. but T could be any region.

if one side gives an immutable T, like from `main`, and one side gives a mutable T, like from `bork`, it might not work right?

is there a way to enforce that T needs to be of the same region as its container? like:

```
pure func zork<m' imm, T>(a &m'[]m'T, b &m'[]m'T) int { 42 }
```










we cant just use a placeholder everywhere we currently use a coord
we need that coords' ownership

we'll also want the regions' mutability.. maybe we'll want the same arrangement?

no, should be a name that we can look up in the env i think, cuz we can turn a region immutable at will.





every time we do a pure call, would we make a new region instance? i dont think so.

when we do a pure call that merges multiple regions, we *could* keep track of what original regions it came from. and maybe we could even use those to verify some sort of universal reference thats coming in?

right now when we make a universal reference, it points at the latest merged region. later on when we try to use it, we get a seg fault. honestly maybe thats a good thing?




what happens if we do allow multiple regions in one allocation? is that bad?

perhaps if we disallow moving the container if the contained region is imm?

does the "contained"/covariant thing help  or something like it?

if we can nail it, then we can put an array in its own region and mutex lock it, instead of HGM basically.





nm start w pure calls.
just have a thing in the func env, vector of regions' mutabilities





