This is Vale's master plan to become the fastest compiling language
**of all time!**

# Language Changes

## Type-Erasure For Faster Compile Times

### What's Type Erasure?

Java/Scala/etc have type-erasure: as part of compilation, `List<Textends Whatever>` becomes `List<Object>`, because the JVM has no concept of generics.

Typescript also has type-erasure in this way, because Javascript has no
concept of generics.

They avoid monomorphizing, which speeds up their compilation.

However, nobody's been able to do this with a native language, yet.

Go programmers manually do something **almost** like this. Whenever a Go
programmer would want a `List<T>`, they instead make a List class which
uses interface{} (basically, Go's version of a Java Object).

The idea: **Vale should do this automatically in debug mode.** Vale
would then be the first language with the compilation speed of Go, and
the run-time speed approaching Rust.

### Why is Type Erasure Faster to Compile?

Type erasure is faster to compile because we only have to generate
machine code (or JVM, in this case) for `List<Object>`, not all the
instantiations (`List<Spaceship>`, `List<Boat>`, etc).

If we can hand less functions to LLVM, the entire compiler gets faster.
See [[Previous Compiler Performance and
LLVM]{.underline}](https://pling.jondgoodwin.com/post/compiler-performance/),
which shows how LLVM takes the majority of compilation time. So if we
can hand less code to LLVM, we speed up compilation time.

### Why can Java and Scala do that, but not C++ and Rust?

#### Because Everything is a Reference

Existing languages (Rust, C++, etc) cannot do this, because generics can
change the size of an object.

 * In C++, `std::array<Vec3, 2>` is bigger than `std::array<Vec2, 2>`

 * In Java, `Tuple2<Vec3, Vec3>` is the same size as `Tuple2<Vec2, Vec2>`, because under the hood, Tuple2 *only contains references.*

In other words, in Java, a generic class only ever holds references to
T, so it doesn't matter what size T is.

#### Because Everything is an Interface\*

In the JVM, everything is an Object. There's not a significant
difference between a Spaceship and a Boat in the JVM (this is why JVM
can support dynamically typed languages like Clojure).

(\* Indeed, Object isn't technically an interface, but one can think of
it that way; it's an abstraction, where we can call methods without
knowing the actual underlying type)

When we have this in Java:

```
public <T extends Printable> void printThrice(T thingToPrint) {
  thingToPrint.print();
  thingToPrint.print();
  thingToPrint.print();
}
```

it still compiles down to this:

```
public void printThrice(Object thingToPrint) {
  thingToPrint.print();
  thingToPrint.print();
  thingToPrint.print();
}
```

At run-time, the JVM will look into the Object's vtable and find the
print method (via looking up a hash of the print() prototype).

So, it doesn't matter that type-erasure lost the original type of
thingToPrint; it can find the method to call, so it will behave
correctly.

##### Just Like Go

This is similar to how Go programmers make "generic" methods without
generics: they make the methods receive interface parameters instead.

### Can a native language do that?

A native language would need to:

 * Make everything a reference.

 * Make it so generic methods accept interfaces instead.

Vale can actually do both!

#### Make Everything into a Reference

Vale can shove everything onto the heap, without changing a program's semantics.

For example, Vale's `tuple<Vec3, Vec3>` would under the hood
become `tuple<^Vec3, ^Vec3>` and the program would behave in exactly the
same way (not counting memory and run-time).

#### Turn Generic Bounds into Interfaces

Vale can automatically turn generic bounds into interfaces.

It can turn this:

```
fn printThrice<T impl Printable>(thingToPrint &T) {
  thingToPrint.print();
  thingToPrint.print();
  thingToPrint.print();
}
```

into this:

```
fn printThrice(thingToPrint &Printable) {
  thingToPrint.print();
  thingToPrint.print();
  thingToPrint.print();
}
```

and update all the call sites as well.

We turned bounds into interfaces, but we can go further: let's turn
**concepts** into interfaces as well.


### To sum up...

Depending on debug vs release, Vale could do different things:

In Development/Debug mode:

 * Make everything into a reference.
 * Turn generic bounds into interfaces.
 * Update call-sites accordingly.

and suddenly, we have much faster build times!

In Release mode, we would do what the user specified, for maximum
run-time speed.

## Erasure-Enabled Incremental Compilation

With incremental compilation, the compiler remembers the .vast output
from previous compilations, in the "incremental-compilation cache".

In C++, when we change a template (e.g. `List<T>`), we have to:

 * Update all the instantiations (`List<Spaceship>`, `List<Boat>`)
 * Update *everyone* that uses them.

However, in Java, when we update the `List<T>` class, we only have to
update...

 * Just the resulting List.class file. That's it.

You can see how type-erasure makes incremental compilation much faster.
It allows us to skip the .vale -> .vast phase, which is very expensive.

So, if Vale has type erasure in debug mode, then we can update much less
every time the user changes something.

## Server-Side Pre-Compilation

This builds upon the aforementioned type erasure.

Normally, when a function has generics, it will instantiate multiple
copies of itself into the resulting .vast; `fn add<T>(self &List<T>, val T)` might become `add<int>`, `add<str>`, `add<Spaceship>`, and so on.

But with type erasure, it doesn't care who uses it, it just generates
one add function in the .vast.

This is really nice, because no matter who uses it, the .vast is the
same.

So let's **distribute the .vast from our servers!**

The client will download it at the same time as the .vale and it will
pre-populate the user's incremental-compilation cache.

This would make initial builds as fast as incremental builds!

Notes:

 * This is only used for debug builds. We still need to distribute the .vale, for release builds (and for modules exporting metaprogramming, see below).

Drawbacks:

 * Doesn't work for a dependency that has any metaprogramming; we can't turn metaprogramming into interfaces.
    * For those we could fall back on the .vale files.
    * Or, perhaps we can type-erasure those as well! (See below)
 * Doubles the download bandwidth.
    * Perhaps we can lazily download the .vale when they finally do a release build?
 * Could be very expensive for the server.
    * We could run these compile jobs less often; every day or so.
    * We could run these compile jobs after a certain number of downloads.
    * We could fund with donations, and automatically cut off this optimization when donations run out.
    * We could distribute the compilation to client machines somehow?
       * Would need some randomization to be able to trust it perhaps.
       * Clients could do doublechecks in the background. If we hit a certain threshold of "hey, that's not right" from the client, then the server does its own build to doublecheck.

## Concepts Into Interfaces

The aforementioned type-erasure doesn't work for anything that uses
concepts. Maybe we can address that.

### What's a Concept?

The **concept** concept is from C++. It says: check that a given type
has the correct methods, *without requiring them to formally extend an
interface.*

If Vale had concepts, it could look something like this (maybe):

```
interface Addable<T> {
  fn +(a T, b T) T;
}
fn triple<T like Addable<T>>(a T) {
  ret a + a + a;
}
```

Here, we're saying that T should have all the methods that `Addable<T>`
has, **but** T **doesn't have to implement** `Addable<T>`.

We would use the above concept like this:

```
struct Vec2 imm { x int; y int; }

fn +(a Vec2, b Vec2) {
  Vec2(a.x + b.x, a.y + b.y
}

fn main() {
  q = triple(Vec2(5, 6)); // Compiler checks existence of + method for Vec2
  // q is Vec2(15, 18)
}
```

Note how there is no:

```
impl Addable<Vec2> for Vec2 { ... }
```

It is unnecessary; **a concept just describes requirements for a type.**

### Turn Concepts into Interfaces

In C++, a concept is different from an interface.

A C++ concept:

```
template<typename T>
concept Hashable = requires(T a) {
  { std::hash<T>{}(a) } -> std::convertible_to<std::size_t>;
};
```

A C++ interface:

```
class Hashable<T> {
public:
  virtual std::size_t hash(const T& a) = 0;
}
```

However, in Vale, they're the same thing:

```
interface Hashable<T> {
  fn hash(a &T) u64;
}
```

we just use them differently. Recall the above example where we had the **like** keyword:

```
fn triple<T like Addable<T>>(a T) {
  ret a + a + a;
}
```

and to use it like a bound, the user would use the **impl** keyword
instead:

```
fn triple<T impl Addable<T>>(a T) {
  ret a + a + a;
}
```

So what if we do that automatically, in debug mode?

Under the hood, Vale in debug mode would:

 * Turn all **like** into **impl**.
 * Update all the call sites to assemble the appropriate vtable (like Go does).

...and suddenly we can use type erasure on all of our functions that
use concepts!

## Metaprogramming Into Runtime Polymorphism

The aforementioned techniques don't work on metaprogramming. Maybe we
can address that.

For example,

```
fn printAll<T... like Printable>(things... T) {
  {...
    print(things...);
  }
}
```

We might be able to transform that into:

```
fn printAll(things Array<Printable>) {
  foreach thing in things {
    print(thing);
  }
}
```

Perhaps we can do this for more advanced metaprogramming capabilities as well?

# Implementation

## Reduce Hash Code Calculations

### Once, Eagerly, On Construction

We'll make it so every object stores its hash code in a field,
calculated when it's constructed. Calling hashCode on the object will
just return that field.

### Once, Lazily

Like the above, but we'll make it so that field is calculated lazily,
on demand.

**Multithreading:**

If we keep hash calculations deterministic and pure, we might not
actually need synchronization primitives here.

If we do need a synchronization primitive here, we can decide to only
lock the object if we find we haven't calculated it yet. We don't need
a synchronization primitive to check. (Though, we would want to check
again once we've acquired the lock)

## Reduce equals() Overhead

Just like hashCode is deeply recursive, equals() probably is as well.

To fix this, we should do interning. Have a giant hash map in the sky,
per object, that serves as our cache for the "canonical instance".

**Multithreading:**

This can be a thread-local cache, and when we send objects into the
outside world they'd be adjusted to use the real world's cache. (maybe
we can use a forwarding reference to help with this)

## Overload Indexing

### Setting Up The Overload Index

We'll eagerly scatter possible overloads. Example:

```
interface ISpaceship { }
struct Firefly impl ISpaceship { }
struct Raza impl ISpaceship { }
interface IWeapon { }
struct Laser impl IWeapon { }
struct Missile impl IWeapon { }
fn equip(ship ISpaceship, weapon IWeapon) { ... }
```

(Let's say this function's FunctionId is equip#42)

We'd have a OverloadSet:

```
struct OverloadSet {
  paramIndexToKindToOverloads
      HashMap<int, HashMap<Kind, HashSet<FunctionId>>>;
}
```

which would contain:

 * 0, ISpaceship, equip#42
 * 0, Firefly, equip#42
 * 0, Raza, equip#42
 * 1, IWeapon, equip#42
 * 1, Laser, equip#42
 * 1, Missile, equip#42

To make it interesting, let's have an additional equip method which
works on armors:

```
interface IArmor { }
struct Plating impl IArmor { }
struct SpikyShield impl IArmor, IWeapon { }
fn equip(ship ISpaceship, weapon IArmor) { ... }
```

(Let's say this function's FunctionId is equip#73)

(Note how SpikyShield is also a Weapon)

With that, our total OverloadSet contains:

 * 0, ISpaceship, \[equip#42, equip#73\]
 * 0, Firefly, \[equip#42, equip#73\]
 * 0, Raza, \[equip#42, equip#73\]
 * 1, IWeapon, equip#42
 * 1, Laser, equip#42
 * 1, Missile, equip#42
 * 1, IArmor, equip#73
 * 1, Plating, equip#73
 * 1, SpikyShield, \[equip#42, equip#73\]

### Resolving Overloads

Now let's try an overload resolution; let's call equip(Firefly,
IWeapon). We'll:

 * Look up anything matching 0, Firefly. We find \[equip#42, equip#73\].
 * Look up anything matching 1, IWeapon. We find \[equip#42\].
 * Do an intersection of the above \[equip#42, equip#73\] and \[equip#42\].

Resulting in only equip#42! Perfect!

Let's try a trickier one, let's call equip(ISpaceship, SpikyShield).
We'll:

 * Look up anything matching 0, ISpaceship. We find \[equip#42, equip#73\].
 * Look up anything matching 1, SpikeShield. We find \[equip#42, equip#73\].
 * Do an intersection of the above \[equip#42, equip#73\] and \[equip#42, equip#73\].

Resulting in both \[equip#42, equip#73\].

We give a compile error, because we don't know which one to call.

The user will have to upcast that SpikyShield to clarify which one they
want.

### Handling Generics

When setting up the index, we can't know if there's going to be a e.g.
`List<int>` and `List<str>`. So, when we call `add(List<int>, int)`,
we'll only see `add(List<T>, T)`.

At that point we'll have to run the generics engine to see if the
constraints are met.

### Other Names and Arities

Of course, the above only applies to functions named equip that have 2
parameters. There might be other equip functions, and functions of other
names. We'll store those all in a `HashMap<OverloadsKey, OverloadSet>`,
where OverloadsKey is:

```
struct OverloadsKey {
  name str;
  numParams int;
}
```

## Parallelize Inter-Module

Before we do this, we should:

 * Switch to generics, and move monomorphization out of Templar and into another phase
 * Make sure the compiler is deterministic and will stay that way.

Temputs will be hierarchical; a Temputs can have a parent Temputs that
it would look in if it doesn't know something. Any parent Temputs will
be immutable.

We'll parallelize on a per-module basis at first. Do the leaves in
parallel, then the ones above it, and so on. This should work and have
no racing and no merging problems, because inter-module dependencies are
a DAG, and there's no mono shenanigans going on.

## Parallelize Intra-Module

Afterward, we'll parallelize even within a module.

 * Build up the relationships of what structs implement what interfaces.
 * Look at all the functions, and build up the overload index.
 * Chuck ready structs/interfaces/functions onto the queue, as work items.
    * "ready" means it only depends on things that have already been compiled.
       * This is tricky with our overloading.
       * There could be circular dependencies, at which point we'll be deadlocked.

Return type inference could slow us down, we might be evaluating it
twice in two different workers.
