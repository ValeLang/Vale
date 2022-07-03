(Draft)

This page talks about templating, and both forms of pattern matching
(statements, function parameters), and how they interact.

# Statements

statements have one purpose: taking values from expressions and putting
them into locals.

For example, after x = 4;, x will refer to a value 4.

## Destructuring

statements can receive a struct and put all of its members into locals.

To set the scene, let\'s say we have a struct Vec4f:

> struct Vec4f {
>
> x float;
>
> y float;
>
> z float;
>
> w float;
>
> }

and a function called getVec which returns one:

> fn getVec() Vec4f { \... }

We want to call that function, and print out all the members. The
obvious way would be this:

> myVector = getVec();
>
> doutln(myVector.x, myVector.y, myVector.z, myVector.w);

If we wanted to make that doutln line less noisy, we could make some
intermediate variables:

> myVector = getVec();
>
> x = myVector.x;
>
> y = myVector.y;
>
> z = myVector.z;
>
> w = myVector.w;
>
> doutln(x, y, z, w);

Destructuring is similar: it creates intermediate variables from the
members of a struct:

> myVector Vec4f(x, y, z, w) = getVec();
>
> doutln(x, y, z, w);

This can sometimes make things much more readable.

## Anatomy of a Pattern

There are three parts to any pattern:

-   The local name,

-   the type,

-   the destructure.

All of these are optional.

For example, for is statement:

> myVector Vec4f(x, y, z, w) = getVec();

myVector specifies the variable name, Vec4f specifies the type, and (x,
y, z, w) specifies what to do with the members.

As mentioned before, all of these are optional. We\'ve seen before that
we don\'t need the destructuring:

> myVector Vec4f = getVec();

or even the type:

> myVector = getVec();

We can also leave off the local name:

> Vec4f(x, y, z, w) = getVec();

We can leave off the type:

> myVector (x, y, z, w) = getVec();

This is valid; myVector\'s type can be inferred from the expression on
the right side.

We can even leave off both, and just have the destructuring!

> (x, y, z, w) = getVec();

If we leave off everything, we must put an underscore, which discards
it:

> \_ = getVec();

## Nesting Patterns

We can put patterns into other pattern. This can go arbitrarily deep,
and can be very useful.

Let\'s say we had a struct Car that contained a struct Engine:

> struct Car {
>
> numWheels int;
>
> color str;
>
> engine engine;
>
> }
>
> struct Engine {
>
> power int;
>
> name str;
>
> }

and a function that returned one:

> fn getCar() Car { \... }

We could print out the numWheels, color, power, and name like this:

> car = getCar();
>
> println(car.numWheels, car.color, car.engine.power, car.engine.name);

or we can use destructuring:

> (numWheels, color, engine) = getCar();
>
> (power, name) = engine;
>
> println(numWheels, color, power, name);

We can easily nest those two statements:

> (numWheels, color, (power, name)) = getCar();
>
> println(numWheels, color, power, name);

This can sometimes make things more readable.

# Functions

## Regular Functions

Regular parameters are like:

> fn foo(a Vec3f)
>
> { \... }

We can destructure parameters with square braces:

> fn foo(a Car(numWheels, color)) {
>
> doutln(numWheels); prints: 4
>
> doutln(color); prints: \"red\"
>
> }
>
> foo(Car(4, \"red\"));

## Simple Templates

### Simple Function Templates

We can put in a placeholder (also known as a \"rune\") for the type, to
be figured out when the function is called:

> fn foo\<T\>(a T) {
>
> doutln(a); prints: 3
>
> doutln(typeOf\<T\>().simpleName()); prints int
>
> }
>
> foo(3);

Because we have a \<T\>, a T means a can be any kind of type, figured
out when it is called. Since the user called it with an int, T becomes
Int. If we want to use the type in the body, we just say T.

(note: simpleName is a method that returns the simplified name of the
type)

To require a borrow reference, use an &. T will be inferred to be a
regular owning reference.

> fn foo\<T\>(a &T) {
>
> doutln(typeOf(a)); prints: &MyObject
>
> doutln(typeOf\<T\>().simpleName()); prints: MyObject
>
> }
>
> x MyObject = MyObject();
>
> foo(&x);

To require an owning reference, use an \^.

> fn foo\<T\>(a \^T) {
>
> doutln(typeOf(a).simpleName()); prints: MyObject
>
> doutln(typeOf\<T\>().simpleName()); prints: MyObject
>
> }
>
> x MyObject = MyObject();
>
> foo(x);

You can use a rune multiple times:

> fn min\<T\>(a T, b T) T {
>
> if (a \< b) { a } else { b }
>
> }

You can use multiple runes:

> fn min\<X, Y\>(a X, b Y) {
>
> return a + b;
>
> }

(note: the above can only be called if there exists a + function that
takes the X and Y.)

We can even combine destructuring with templates:

> fn foo\<T\>(car T(numWheels, color)) {
>
> doutln(typeOf(car).simpleName()); prints: Car
>
> doutln(numWheels); prints: 4
>
> doutln(color); prints: \"red\"
>
> }
>
> foo(Car(4, \"red\"));

#### Specifying Runes

As a starting point, let\'s say we wanted a function that would wrap
every argument in a Foo:

> fn wrapWithFoo\<A, B, C\>(a A, b B, c C) {
>
> (Foo(a), Foo(b), Foo(c))
>
> }
>
> wrapWithFoo(14, 15, 16);

but we wanted to make this function generic, instead of just being for
Foo. We can make it templated:

> fn wrap\<F, A, B, C\>(a A, b B, c C) {
>
> (F(a), F(b), F(c))
>
> }
>
> wrap\<Foo\>(14, 15, 16)

Notice the wrap\<F\>. Putting a rune right after the function name means
the user can specify that manually, like wrap\<Foo\>.

### Simple Struct Templates

Just like with functions, we can put runes right after the struct\'s
name. Functions are also able to put runes inside their parameters, but
structs have no parameters, so they can only have runes after their
name.

Here\'s a basic linked list node class:

> struct ListNode\<E\> {
>
> value E;
>
> next ListNode\<E\>;
>
> }

## Template Rules

Simple templates are nice, but what about when we want to do more
advanced things? We might want to:

-   Make a struct whose T is an integer.

-   Make a function whose T must be a borrow or shared reference,

-   Make a function whose T must extend IMyInterface,

-   Make a function whose T is a function that turns a Str into an #R,

-   Make a function whose T is a List of something that extends
    > IMyInterface.

For these, we bring out the big guns: template rules.

### 

There are various kinds of basic rules:

-   integer rules, for example X int = 2 \| 3 \| 4

-   boolean rule, for example X bool

-   ownership rule, for example X ownership = share \| borrow \| raw

-   permission rule, for example X permission = rw \| xrw

-   kind rule, for example X kind = str \| int \| bool.\
    > There are many different flavors of kind:

    -   struct: X struct

    -   interface: X interface

    -   sequence: X pack = \[A, B, C, D\]

-   ref rule, for example X Ref(O, L, P, K)

-   prototype: X prototype = Prot(\"add\", (A, B), R)

-   relationship rule, for example, implements(X, K) or R.kind = int.

These are all explained in more detail below.

### Int Rules

One use of template rules is to have runes represent things other than
types, such as integers.

Let\'s imagine we had classes Vec2f, Vec3f, and Vec4f which represented
coordinates in space:

> struct Vec2f { x float; y float; }
>
> struct Vec3f { x float; y float; z float; }
>
> struct Vec4f { x float; y float; z float; w float; }

and we wanted to have a struct template, instead of three different
structs.

The first step would be to make the structs look as similar as possible
(which is always a good idea when trying to figure out how to template
something). We can make the structs use sequences:

> struct Vec2f { \[2 \* Float\]; }
>
> struct Vec3f { \[3 \* Float\]; }
>
> struct Vec4f { \[4 \* Float\]; }

The only difference here is the size of the sequence. If only we could
make a template rune #N to be a placeholder for that number!

Perhaps it would look something like this:

> struct Vecf\<N\> {
>
> \[N \* Float\];
>
> }

but that doesn\'t work, because by default, any rune will be a
placeholder for a type (note: more specifically, a reference, see Types:
References and Kinds for more). The compiler will expect an integer to
the left of the \* but we gave it a rune for a type.

We need to specify that #N is an integer:

> struct Vecf\<N\>
>
> rules(N int)
>
> {
>
> \[N \* Float\];
>
> }

and voilà, we have a templated Vec struct.

If we wanted to, we could even restrict what #N could be:

> struct Vecf\<N\>
>
> rules(N int = 2 \| 3 \| 4)
>
> {
>
> \[N \* Float\];
>
> }

### Types: References and Kinds

To harness the power of rules, we must speak their precise language.
Let\'s not think in terms of \"type\", but the more precise terms,
\"references\" and \"kinds\".

For example, in x inl &!MyObject, MyObject is the kind, but the whole
inl &!MyObject is the reference. More generally, a reference is the
combination of:

-   ownership (& in the example), such as owning, borrow, shared, or
    > raw.

-   permission (! in the example), such as readonly or readwrite.

-   location (inl in the example), such as inline or heap.

-   kind (MyObject in the example), as explained below.

As opposed to **reference**, a kind can be thought of as the
**referend**; in other words, the sort of object that we\'re pointing
at. Our reference might be pointing at the MyObject in an owning way or
a borrowing way, a const way or a mutable way, and our reference might
know that the MyObject is on the stack or the heap, but either way, it
is pointing at a MyObject.

Each variable is a reference. For example, in fn foo(x MyObject) { \...
}, x\'s reference is inl \^!MyObject, which means it\'s an inline owning
readwrite MyObject reference. Even though we only specified the kind, it
defaulted to owning, readwrite, and inline (note: it will smartly figure
out a default from whether or not it\'s moved away).

Now that we understand references and kinds, let\'s see some Ref rules
in action.

### Ref Rules

A basic templated function with no rules:

> fn moo(a T) { \... }

Radon can figure out that because it\'s used as a parameter, it must be
a reference, but we could make it explicit if we wanted to:

> fn moo
>
> rules(#T: Ref)
>
> (a T)
>
> { \... }

Remember that a reference has four parts:

-   ownership, such as owning, borrow, shared, or raw,

-   permission, such as readonly or readwrite,

-   location, such as inline or heap,

-   kind.

We can establish a relationship between our reference #T and runes for
its four parts, like this:

> fn moo
>
> rules(#T: Ref\[#O, #P, #L, #K\])
>
> (a T)
>
> { \... }

Now, we have its ownership #O, its permission #P, its zone #Z, and its
kind #K.

See the next section for how we might use these.

### Ownership, Permission, Location Rules

In the previous example we had a reference #T and its four parts,
including its ownership #O:

> fn moo
>
> rules(#T: Ref\[#O, #P, #L, #K\])
>
> (a T)
>
> { \... }

We could add a rule specifying that its ownership is either borrow,
share, or raw:

> fn moo
>
> rules(
>
> #T: Ref\[#O, #P, #L, #K\],
>
> #O: Ownership = borrow\|share\|raw)
>
> (a T)
>
> { \... }

Now, we can only pass in borrow, share, or raw references. Trying to
pass in an owning reference would result in a compile error.

We can do the same thing for permissions and locations as well:

> fn moo
>
> rules(
>
> #T: Ref\[#O, #P, #L, #K\],
>
> #L: Ownership = borrow\|share\|raw,
>
> #P: Permission = readonly,
>
> #Z: Location = movable)
>
> (a T)
>
> { \... }

### Nesting Rules

We can simplify the above. The first step would be to nest the rules:

> fn moo
>
> rules(
>
> #T: Ref\[
>
> #O: Ownership = borrow\|share\|raw,
>
> #P: Permission = readonly,
>
> #L: Location = inline,
>
> #K\])
>
> (a T)
>
> { \... }

And we can further simplify:

-   Since the #O: Ownership = borrow\|share\|raw is in the ownership
    > position of the Ref rule, we can leave off the : Ownership.

-   Since the #P: Permission = readonly is in the permission position of
    > the Ref rule, we can leave off the : Permission.

-   Since the #L: Location = inline is in the location position of the
    > Ref rule, we can leave off the : Location.

This gives us:

> fn moo
>
> rules(
>
> #T: Ref\[
>
> #O = borrow\|share\|raw,
>
> #P = readonly,
>
> #L = inline,
>
> #K\])
>
> (a T)
>
> { \... }

In fact, since we\'re not using #O, #P, #L, or #K, anywhere, we can take
those out too:

> fn moo
>
> rules(
>
> #T: Ref\[
>
> borrow\|share\|raw,
>
> readonly,
>
> inline,
>
> \_\])
>
> (a T)
>
> { \... }

and now it can even fit on one line!

> fn moo
>
> rules(
>
> #T: Ref\[borrow\|share\|raw, readonly, inline, \_\])
>
> (a T)
>
> { \... }

### Kind Rules

Let\'s say we wanted our reference to point at a List:Int:

> fn moo
>
> rules(
>
> #T: Ref\[\_, \_, \_, #K\],
>
> #K: Kind = List:Int)
>
> (a T)
>
> { \... }

\...which can be simplified to:

> fn moo
>
> rules(
>
> #T: Ref\[\_, \_, \_, List:Int\])
>
> (a T)
>
> { \... }

Whereas Ref has four details (ownership, permission, location, kind),
Kind only has one detail (mutable or immutable), and we can capture it
in a similar way, with square braces.

For example, let\'s say we wanted our reference to be pointing at a kind
that is immutable:

> fn moo
>
> rules(
>
> #T: Ref\[\_, \_, \_, #K\],
>
> #K: Kind\[#M\],
>
> #M: Mutability = Mutable)
>
> (a T)
>
> { \... }

We can simplify the above to this:

> fn moo
>
> rules(
>
> #T: Ref\[\_, \_, \_, Kind\[Mutable\]\])
>
> (a T)
>
> { \... }

### Relations

There are various constraints that we can impose on our rules.

Let\'s say we wanted a function that takes in something that\'s a
subclass of MyInterface, we\'d use the isa relation:

> fn moo
>
> rules(
>
> #T: Ref\[\_, \_, \_, #K\],
>
> #K isa MyInterface)
>
> (a T)
>
> { \... }

If we wanted a function that takes in something that\'s meets all the
requirements MyInterface (think Go interfaces vs any other language\'s
interfaces/traits), we\'d use the like relation:

> fn moo
>
> rules(
>
> #T: Ref\[\_, \_, \_, #K\],
>
> #K isa MyInterface)
>
> (a T)
>
> { \... }

like will make sure that the left thing meets the requirements of the
right thing.

If we want something that\'s callable, we could use the shorthand for
the IFunction interface:

> fn moo
>
> rules(
>
> #T: Ref\[\_, \_, \_, #K\],
>
> #K like fn(Int)Bool)
>
> (a T)
>
> { \... }

This makes sure that #K is callable.

There are other relations:

-   size(#K): which evaluates to the size of kind #K

-   \<, \>, \<=, \>=

-   (todo: list more of them here)

### Variadic Templates

We can make functions that take in any number of any kind of argument.
For example, let\'s reimplement the println function, which calls print
on all of its arguments, and then prints a newline.

First, let\'s see the function signature:

> fn println(a: \...#T)

The \... makes a into a **pack**. A pack cannot be used directly, it
must be expanded with special syntax:

> fn println(a: \...#T) {
>
> \... print(a\...);
>
> print(\"\\n\");
>
> }

Inside the function body, we see two ... operators. They look the same
but are very different:

-   If at the beginning of a expression (like our body\'s first \...),
    > then it\'s the **repeater** operator; it says, \"This expression
    > will be repeated\...\"

-   If after a pack (like our body\'s second \...), then it\'s the
    > **explode** operator; it says, \"\...for each one of these\"

So, our function says that we\'re repeating the print call for each
element of a.

If we were to call it like println(3, \"hello\", 7), then a contains 3,
\"hello\", and 7, so our function would effectively be this:

> fn println(a1 int, a2 str, a3 int) {
>
> print(a1);
>
> print(a2);
>
> print(a3);
>
> print(\"\\n\");
>
> }

#### Another Example

Let\'s say we wanted a function that will println, but in the color
blue.

> fn blueprintln(a: \...#T) {
>
> blue = terminal.pushColor(\"blue\");
>
> println(\...a\...);
>
> }

It may look special, but these are the same **repeater** and **explode**
operators. The first one is saying \"this expression will be repeated\"
and the second one is saying \"for each element of a\".

This is a common thing to see, and even has a name, \"variadic
forwarding\".

### Repeating Statements vs Expressions

Let\'s say we wanted a function named excited that would append the
string \"!!!\" to every one of its arguments, and return all of those
arguments.

We would hope to use it like this:

> (sparkleSuffixed, boggleSuffixed, numSuffixed, woggetSuffixed) =
>
> excited(\"sparkle\", \"boggle\", 1337, \"wogget\");
>
> doutln(sparkleSuffixed); should print \"sparkle!!!\"
>
> doutln(boggleSuffixed); should print \"boggle!!!\"
>
> doutln(numSuffixed); should print \"1337!!!\"
>
> doutln(woggetSuffixed); should print \"wogget!!!\"

Here\'s what excited look like, without variadic templates:

> fn excited(a A, b B, c C, d D) {
>
> (a + \"!!!\", b + \"!!!\", c + \"!!!\", d + \"!!!\")
>
> }

Here\'s what it would look like with variadic templates:

> fn excited(things: \...#T) {
>
> (\... things\... + \"!!!\");
>
> }

Remember, the repeater operator (the first \...) says \"this expression
will be repeated\", and \"this expression\" refers to the entire
expression, including the + \"!!!\".

One might wonder, why do we need the parentheses? That\'s because the
repeater operator has these rules:

-   If after a (, then the resulting expressions will be separated by
    > commas and surrounded by parentheses.

-   If it\'s the first thing in the entire statement, the resulting
    > expressions will separated by semicolons.

If we left out the parentheses in our example, it would have resulted in
a function effectively like this:

> fn suffix(a A, b B, c C, d D) {
>
> a + \"!!!\";
>
> b + \"!!!\";
>
> c + \"!!!\";
>
> d + \"!!!\";
>
> }

which is not what we wanted.

#### Another Example

To build on the last example with blueprintln, let\'s say we wanted a
function named \"excitedblueprintln\" that printed every argument,
suffixed with \"!!!\", on one line, ending with a newline, in blue.

We would hope to use it like this:

> excitedblueprintln(3, \"hello\", true); should print
> 3!!!hello!!!true!!!

The function would look like this:

> fn excitedblueprintln(things: \...#T) {
>
> blueprintln(\...excited(things\...));
>
> }

When we give 3, \"hello\", and true, that function becomes:

> fn excitedblueprintln(a int, b str, c bool) {
>
> blueprintln(excited(a), excited(b), excited(c));
>
> }

We could also have phrased it like this:

> fn excitedblueprintln(things: \...#T) {
>
> blueprintln(\...excited(\...things\...)\...);
>
> }

which, when called, would have become:

> fn excitedblueprintln(a int, b str, c bool) {
>
> blueprintln(\...excited(a, b, c)\...);
>
> }

which would have been as if we had this:

> fn excitedblueprintln(a int, b str, c bool) {
>
> blueprintln(a + \"!!!\", b + \"!!!\", c + \"!!!\");
>
> }

# Templates in Patterns

This power isn\'t just for function signatures, we can use all of these
techniques inside statements as well!

## Capturing Runes in Patterns

We can capture a type in a rune in a statement:

> fn main() {
>
> a T = 3;
>
> doutln(typeOf\<T\>().simpleName()); prints int
>
> }

Notice how that even though main isn\'t a templated function, we can
still use runes inside it.

## Packs in Patterns

Let\'s take our example excited function from before, which returns
every argument with \"!!!\" appended:

> fn excited(things: \...#T) {
>
> (\... things\... + \"!!!\");
>
> }

and let\'s take our example usage of it from before:

> fn main() {
>
> (sparkleSuffixed, boggleSuffixed, numSuffixed, woggetSuffixed) =
>
> excited(\"sparkle\", \"boggle\", 1337, \"wogget\");
>
> doutln(sparkleSuffixed); should print \"sparkle!!!\"
>
> doutln(boggleSuffixed); should print \"boggle!!!\"
>
> doutln(numSuffixed); should print \"1337!!!\"
>
> doutln(woggetSuffixed); should print \"wogget!!!\"
>
> }

This can actually be simplified to:

> fn main() {
>
> (suffixedThings\...) = excited(\"sparkle\", \"boggle\", 1337,
> \"wogget\");
>
> \... println(suffixedThings\...);
>
> }

Notice how that even though main isn\'t a templated function, we can
still capture and explode packs.

## Packs with Destructuring

Remember how we can destructure a struct to get all its members:

> struct Car { numWheels int; color str; miles int; upgraded bool; }
>
> fn main() {
>
> myCar = Car(4, \"Battlecruiser\", 7000000, true);
>
> Car\[n, c, m, w\] = myCar;
>
> doutln(n); prints 4
>
> doutln(c); prints \"Battlecruiser\"
>
> doutln(m); prints 7000000
>
> doutln(w); prints false
>
> }

We can capture the members as a pack instead:

> struct Car { numWheels int; color str; miles int; upgraded bool; }
>
> fn main() {
>
> myCar = Car(4, \"Battlecruiser\", 7000000, true);
>
> Car\[fields\...\] = myCar;
>
> \... doutln(fields\...);
>
> }

## Complex Example

Let\'s imagine we had classes Vec2f, Vec3f, and Vec4f which represented
coordinates in space:

> struct Vec2f { x float; y float; }
>
> struct Vec3f { x float; y float; z float; }
>
> struct Vec4f { x float; y float; z float; w float; }

and some functions to add them together:

> fn add(a Vec2f, b Vec2f) {
>
> Vec2f(a.x + b.x, a.y + b.y)
>
> }
>
> fn add(a Vec3f, b Vec3f) {
>
> Vec3f(a.x + b.x, a.y + b.y, a.z + b.z)
>
> }
>
> fn add(a Vec4f, b Vec4f) {
>
> Vec3f(a.x + b.x, a.y + b.y, a.z + b.z, a.w + b.w)
>
> }

and we wanted to use templating, to reduce all of this boilerplate and
repeated code.

First step would be to make the structs look as similar as possible
(which is always a good idea when trying to figure out how to template
something). We can make the structs use sequences:

> struct Vec2f { \[2 \* Float\]; }
>
> struct Vec3f { \[3 \* Float\]; }
>
> struct Vec4f { \[4 \* Float\]; }
>
> fn add(a Vec2f, b Vec2f) {
>
> Vec2f(a.0 + b.0, a.1 + b.1)
>
> }
>
> fn add(a Vec3f, b Vec3f) {
>
> Vec3f(a.0 + b.0, a.1 + b.1, a.2 + b.2)
>
> }
>
> fn add(a Vec4f, b Vec4f) {
>
> Vec3f(a.0 + b.0, a.1 + b.1, a.2 + b.2, a.3 + b.3)
>
> }

Now, let\'s make that struct into a templated one:

> struct Vecf\<N\>
>
> rules(#N int)
>
> {
>
> \[#N \* Float\];
>
> }

(The rules(#N int) is there because otherwise it would assume #N is a
ref)

And our add function could look like:

> fn add(a Vecf\<N\>, b Vecf\<N\>) {
>
> Vecf\<N\>\[aParts\...\] = a;
>
> Vecf\<N\>\[bParts\...\] = b;
>
> T(\... (aParts\...) + (bParts\...))
>
> }

Notice how we\'re using two exploder \...s with one repeater \.... This
is completely valid, because the two packs (aParts and bParts) are of
the same length. If they weren\'t, it would result in a compile error.

In fact, we can simplify this further by doing the destructuring right
there in the parameters:

> fn add(\_ Vecf\<N\>\[aParts\...\], \_ Vecf\<N\>\[bParts\...\]) {
>
> T(\... (aParts\...) + (bParts\...))
>
> }
