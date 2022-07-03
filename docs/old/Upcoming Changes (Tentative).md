[[References]{.underline}](#references)

[[Variability]{.underline}](#variability)

[[Structs\' Mutability]{.underline}](#structs-mutability)

[[Structs\' Mutability v2]{.underline}](#structs-mutability-v2)

[[Different Lambda Syntax]{.underline}](#different-lambda-syntax)

[[Must Explicitly Capture
Instances]{.underline}](#must-explicitly-capture-instances)

[[Arrays and Maps]{.underline}](#arrays-and-maps)

> [[Runtime-sized Arrays]{.underline}](#runtime-sized-arrays)
>
> [[Statically-sized Arrays]{.underline}](#statically-sized-arrays)
>
> [[With Generic Parameters]{.underline}](#with-generic-parameters)

[[Location]{.underline}](#location)

[[Notes]{.underline}](#notes)

# References

**TL;DR:** &&Spaceship now means normal non-owning reference. Weak is
now &\*.

Today, there are three kinds of references for mutable structs:

-   **Owning:** Spaceship

-   **Constraint:** &Spaceship.

    -   In Assist mode, halts the program when it becomes dangling.

    -   In other modes, halts the program when it\'s dangling and
        > dereferenced.

-   **Weak:** &&Spaceship

However:

-   Making structs weakable is going to be more expensive than we
    > thought in HGM; we need an extra 8 bytes for any weakable struct.

-   So, the previous recommendation of \"if you dont know if the pointee
    > is alive, use a weak reference\" but that recommendation now has a
    > cost.

So, we\'ll add the Normal reference, which is like a non-assist-mode
constraint reference.

-   **Owning:** Spaceship

-   **Constraint:** &Spaceship.

    -   In Assist mode, halts the program when it becomes dangling.

    -   In other modes, acts like a Normal reference.

-   **Normal:** &&Spaceship Halts the program when it\'s dangling and
    > dereferenced.

-   **Weak:** &\*Spaceship

We\'ll advise that when constraint references are a bit overboard, one
can use normal references.

# Variability

(DONE)

**TL;DR:** x var = 4; and set x = 4;.

Right now, to make it so we can change a local, we add a ! to denote
it\'s a \"varying local.\"

> x! = 4;

and we can later change it with a mutate statement:

> mut x = 4;

We\'ll change those to:

> x var = 4;

and:

> set x = 4;

It\'s opposite will be fin, short for final.

Benefits:

-   Both these keywords give better hints as to what\'s going on.

-   Less chance of confusing this with structs\' mutabilities.

-   Less chance of assuming that this is what makes the referred-to
    > object mutable or immutable (which is controlled by something
    > completely different)

    -   People assume this a lot, sometimes because that\'s how Rust
        > works.

Drawback:

-   Can\'t name any local variables \"set\" anymore =(

# Structs\' Mutability

**TL;DR:** Instead of imm and mut, it will be val and inst:

Today, structs can be either mutable or immutable, see
[[https://vale.dev/guide/structs#mutability]{.underline}](https://vale.dev/guide/structs#mutability).

Immutable structs are shared freely (no move semantics or ownership),
but nothing inside them can change.

> struct Vec2 imm {
>
> x int; // Can\'t put x var int; because imm makes everything deeply
> immutable.
>
> y int;
>
> }

Mutable structs can have things that change; we can add var to any
member:

> struct Spaceship mut {
>
> engine Engine;
>
> navigation var Navigation;
>
> }

Instead of imm and mut, we should use val and inst:

> struct Vec2 val {
>
> x int; // Can\'t put x var int; because imm makes everything deeply
> immutable.
>
> y int;
>
> }
>
> struct Spaceship inst {
>
> engine Engine;
>
> navigation var Navigation;
>
> }

We\'ll refer to this dimension as \"valness\" instead of mutability. (A
better name would be nice? sentience?)

Benefits:

-   They hint a little more at their intended use: value structs, versus
    > anything that has identity.

-   People won\'t confuse this with mutable and immutable variables,
    > which is a very persistent concept. By moving away from
    > \"mutability\" as both a concept on a local and as an (unrelated)
    > concept on structs, we get even further from conflating the two.

    -   And now, when people talk about whether something\'s mutable, if
        > unclear we can ask \"do you mean it\'s varying, or it\'s
        > pointing to an instance?\"

Drawbacks:

-   These keywords no longer communicate that they indirectly factor
    > into whether fields can be varying or not.

# Structs\' Mutability v2

**TL;DR:** Instead of imm and mut, we\'ll control it via the reference.

a Vec2; // mutable, inline

a \^Vec2; // mutable, heap

a #Vec2; // immutable, inline

a \^#Vec2; // immutable, heap

There are very few objects that would want to be both mutable and
immutable, likely mostly collections.

This will construct a normal hash map inline:

fn HashMap\<K, V\>() HashMap\<K, V\> { \... }

and we can even put it on the heap like so:

a = \^HashMap\<int, int\>();

We can also offer an immutable constructor:

fn HashMap\<K, V\>() \*HashMap\<K, V\> { \... }

We can take an existing struct, and make an immutable version of it,
provided all its supplied arguments are immutable. For example:

> struct OuterStruct {
>
> b \^InnerStruct;
>
> }

We can make an immutable one of these by saying:

> inner = \^#InnerStruct(10);
>
> outer = #OuterStruct(inner);

Notice how we\'re handing an \^#InnerStruct in, even though the field
appears to receive a \^InnerStruct.

We generally don\'t have constructors for immutable data, because their
names would collide with the constructors for mutable data. Later on, we
might find a way to overload by return type or something, but for now,
we\'ll just say that the #MyStruct syntax will just construct an
immutable MyStruct.

Interesting note: We could allow converting an inline mutable to an
immutable, if it contains only immutables.

# Different Lambda Syntax

**TL;DR:** (x, y){ x \* y \* 2 } to (x, y) =\> x \* y \* 2

Today, lambdas look like this:

> (x, y){ x \* y \* 2 }

or if there\'s multiple statements:

> (x, y){
>
> z = x \* y \* 2;\
> ret z;
>
> }

and this is shorthand for the first one:

> { \_ \* \_ \* 4 }

these are both valid no-op lambdas:

(){}

{}

**Going forward,** we should keep the shorthand:

> { \_ \* \_ \* 4 }

**But** change the basic syntax to:

> (x, y) =\> x \* y \* 2

and if there\'s multiple statements:

> (x, y) =\> {
>
> z = x \* y \* 2;
>
> ret z;
>
> }

and these would both be valid no-op lambdas:

()=\>{}

{}

Example one-parameter one-line lambda, before and after:

> x = myList.map((i){ Ship(i, i) });
>
> x = myList.map(i =\> Ship(i, i));

The new way is a little more clear near the parameter.

Example two-parameter one-line lambda, before and after:

> x = myList.eachI((i, j){ i + j });
>
> x = myList.map((i, j) =\> i + j);

The new way is a little more clear in the lambda body.

Example one-parameter two-line lambda, before and after:

> obj.addCallback((x){
>
> x.launch();
>
> x.fire();
>
> });
>
> obj.addCallback(x =\> {
>
> x.launch();
>
> x.fire();
>
> });

The new way is a little more clear in the parameters.

Example two-parameter two-line lambda, before and after:

> obj.addCallback((x, y){
>
> x.launch();
>
> y.fire();
>
> });
>
> obj.addCallback((x, y) =\> {
>
> x.launch();
>
> y.fire();
>
> });

Here, the new way actually involves a little more typing.

Benefits:

-   More familiar to JS users

-   Less noise when making a one-parameter or one-line lambda

Drawbacks:

-   A little more typing.

# Must Explicitly Capture Instances

Today, we implicitly capture everything in the parent scope.

> struct Spaceship { fuel int; }
>
> fn main() {
>
> x = stdinReadInt();
>
> ship var = Spaceship(7);\
> myLambda = { set ship = Spaceship(x); };
>
> myLambda();
>
> }

Let\'s change it to implicitly only capture values, but not instances:

> struct Spaceship { fuel int; }
>
> fn main() {
>
> x = stdinReadInt();
>
> ship var = Spaceship(7);\
> myLambda = \[ship\]() =\> { set ship = Spaceship(x); };
>
> myLambda();
>
> }

Benefits:

-   Prevent accidental halts, when vale detects use-after-frees.

Drawbacks:

-   More typing

# Arrays and Maps

TL;DR:

-   arr = \[\]Ship(n)

-   arr = \[\]Ship(n, i =\> Ship(i))

-   arr = \[\](n, i =\> Ship(i))

-   arr = \[#3\]int\[42, 43, 44\]

-   arr = \[#3\]\[42, 43, 44\]

-   arr = \[#100\](i =\> Ship(i))

-   map = \[str\]str(hasher, equator)

-   map = \[str\]str(hasher, equator, capacity)

## Runtime-sized Arrays

Note on semantics: This isn\'t just a syntax change, there\'s a slight
upgrade in semantics here too: today, a runtime-sized array is like in
C++ MyClass\* myArray = new MyClass\[n\]; in that it\'s fixed size, but
determined at runtime. Instead, we\'re making them halfway like
std::vector\<MyClass\>; it will contain a pointer, a size, and a
capacity. We\'ll be able to initialize it to have everything empty.
However, it cannot expand its capacity. It will allocate its entire
capacity up-front, and never change it. All this is necessary to
implement an (Array)List class.

Today, runtime-sized arrays are e.g. Array\<mut, Spaceship\> and
Array\<imm, Spaceship\>

Keep in mind mut is changing to inst, and imm to val, so these *would*
have been

Array\<inst, Spaceship\> and Array\<val, Spaceship\>. But we have better
plans.

Instead, we\'ll use the basic form \[valness\]element_type.

For example, Array\<inst, T\> becomes:

-   fn moo(a \[\]int) { \... }

Arrays are by default inst, and their elements are final.

Some more examples:

-   Declaring an array:

    -   fn moo(a \[\*\]Ship) { \... }

-   Creating an array with a lambda:

    -   arr = \[\*\]Ship(n, i =\> i \* 2);

        -   This fills the entire array, all n capacity.

-   Creating an unfilled array:

    -   arr = \[\*\]Ship(n);

        -   This makes an array of capacity n but size 0.

-   To infer the element type from the lambda, just omit it:

    -   fn moo(a \[\]Ship) { \... } // can\'t omit element type in
        > declaration

    -   arr = \[\*\](n, i =\> i \* 2);

If we want to manually specify inst or val, we can:

-   fn moo(a \[val \*\]Ship) { \... }

-   arr = \[val \*\]Ship(n, i =\> i \* 2);

This will be used instead of today\'s Array\<val, T\>.

If we want to specify the elements be final or varying, we can:

-   fn moo(a \[var \*\]Ship) { \... }

-   arr = \[var \*\]Ship(n, i =\> i \* 2);

We can even specify both:

-   fn moo(a \[inst var \*\]Ship) { \... }

-   arr = \[inst var \*\]Ship(n, i =\> i \* 2);

Note that this shouldn\'t collide with explicitly-capturing lambdas,
which would look like:

> x var = 8;
>
> myLam = \[x\](i)=\>{set x = i;};

## Statically-sized Arrays

Statically-sized arrays are declared like:

-   fn moo(a #3\[int\]) { \... }

We\'ll change it to something similar to the runtime-sized arrays above,
where theres a number in the square brackets, like:

-   \[3\]Ship

And populated by either:

-   List of values, like arr = \[3\]int\[42, 43, 44\]

-   Lambda, like arr = \[3\]int(i =\> i + 42};

    -   And of course, the magic-param lambda, like arr = \[3\]int{\_ +
        > 42};

Some examples:

-   Basic example (recall each element is implicitly final):

    -   fn moo(a \[3\]Ship) { \... }

    -   arr = \[3\]Ship\[makeSerenity(), makeRaza(), makeKestrel()\]

-   If we construct it with values in square brackets, we can leave off
    > the number:

    -   fn moo(a \[3\]Ship) { \... }

    -   arr = \[\]Ship\[makeSerenity(), makeRaza(), makeKestrel()\]

-   We can leave off the type too:

    -   fn moo(a \[3\]Ship) { \... }

    -   arr = \[\]\[makeSerenity(), makeRaza(), makeKestrel()\]

-   We can use a named constant instead:

    -   fn moo(a \[NUM_SHIPS\]Ship) { \... }

    -   arr = \[3\]Ship\[makeSerenity(), makeRaza(), makeKestrel()\]

-   Or even an expression:

    -   fn moo(a \[NUM_SHIPS - 1\]Ship) { \... }

    -   arr = \[2\]Ship\[makeSerenity(), makeRaza()\]

-   We can specify that each element is varying:

    -   fn moo(a \[var 3\]int) { \... }

    -   arr = \[var 3\]int\[3, 4, 5\];

-   We can specify that the array itself is a value:

    -   fn moo(a \[val 3\]int) { \... }

    -   arr = \[val 3\]int\[3, 4, 5\];

-   We can specify both the array\'s valness and the elements\'
    > variability:

    -   fn moo(a \[inst var 3\]Ship) { \... }

    -   arr = \[inst var 3\]Ship(i =\> i \* 2);

-   We can populate it with a lambda even without the type:

    -   fn moo(a \[3\]Ship) { \... }

    -   arr = \[3\](i =\> Ship(i \* 2));

        -   (the difference between this and the start of a lambda is
            > the presence of a =\> *after* the ending parens)

## With Generic Parameters

Recall that we can specify both the array\'s valness and the element\'s
variability:

-   fn moo(a \[inst var 3\]Ship) { \... }

-   arr = \[inst var 3\]Ship(n, i =\> i \* 2);

However, if we have a generic parameter, we\'d be unsure whether it\'s
the array\'s valness or the element\'s variability:

-   fn moo\<Z\>(a \[Z 3\]Ship) { \... }

The solution is to allow commas to clarify. This will specify the
array\'s valness:

-   fn moo\<Y\>(a \[Y, 3\]Ship) { \... }

and this will specify the element\'s variability:

-   fn moo\<Y\>(a \[Y, , 3\]Ship) { \... }

and we can specify both:

-   fn moo\<Y, Z\>(a \[Y, Z, 3\]Ship) { \... }

# Location

**TL;DR:** yon becomes heap.

We can heap-allocate a Spaceship like so:

> struct Spaceship {
>
> engine Engine;
>
> nav Navigation;
>
> }
>
> fn main() {
>
> s = Spaceship(Engine(5), Navigation(\"orbital\"));
>
> }

or on the stack:

> fn main() {
>
> s = inl Spaceship(Engine(5), Navigation(\"orbital\"));
>
> }

The first one, without the the inl, actually has an implicit yon:

> fn main() {
>
> s = yon Spaceship(Engine(5), Navigation(\"orbital\"));
>
> }

yon, for all intents and purposes, basically means heap. So, we\'ll
change it to be heap.

> fn main() {
>
> s = heap Spaceship(Engine(5), Navigation(\"orbital\"));
>
> }

Still, most of the time, the user won\'t even specify it.

Benefits:

-   Much clearer.

Drawbacks:

-   Can\'t have any functions or locals named \"heap\" =(

# Notes

we should have a \`template\` keyword in front of fn to opt-into the
crazy c++ wild-west nonsense.

Whether to bring back !

-   Leaning towards no, it\'s not a great use of an entire symbol.

var x vs x var

-   On the fence here, best explore more

could we take them out for locals? starting to lean towards yes

need to specify length for unknown sized arrays somewhere

require specify inl and heap?

**Favorite**

runtime length, runtime type: IPrintable\[\]

static length, runtime type: IPrintable\[N\]

static length, static types: (\...P like IPrintable)\[\]

\... means \"for each type\"

fn print\<P\>(args \...(P like IPrintable\<P\>)) {

each\... arg in args {

print(arg);

}

}

**Alternative**

can we make a tuple look more like a static array?

a tuple is kind of like a meta static-array. its like a static array of
interfaces where we still remember the specific type of every element.

runtime length, runtime type: \[IPrintable\]

static length, runtime type: \[N; IPrintable\]

static length, static types: \[imm; final; N; \...P like IPrintable\]

tuple: \[imm; final; int, bool, str\]

fn print\<P like IPrintable\>(args \...P) {

each\... arg in args {

print(arg);

}

}

can we make a tuple into a meta static-array?

a tuple is kind of like a meta static-array. its like a static array of
interfaces where we still remember the specific type of every element.

fn serializeJSON\<T\>(myStruct T) where T isa struct {

println(\"{\");

#each index, key, val in #fields(T) {\
#if index \> 0 {

println(\",\");

}\
println(key + \":\" + serializeJSON(val));\
}

println(\"{\");

}

fn print(x \[\]) { }

fn print\<T\>(x T)

rules(tuple T, len(T) \> 0) {

print(x.0);

print(x.tail());

}

print(\[\"hello\", 42, true\]);

fn print\<T\>(tup T)

rules(tuple T) {

each tup (x){

print(x);

}

}

print(\[\"hello\", 42, true\]);

interface IPrintable\<T\> {

fn print(obj T);

}

fn print\<P\>(\... args #(P each IPrintable)\[\]) {

#each arg in args {

print(arg);

}

}

fn print\<P\>(\... args #P\[\])

where P each IPrintable {

#each arg in args {

print(arg);

}

}

fn print\<P\>(\... args #P\[\])

where P each {exists(fn print(\_)void)} {

#each arg in args {

print(arg);

}

}

{\$ \* 5}

fn print\<P\>(\... args #P\[\])

where P each {exists(fn print(\$)void)} {

#each arg in args {

print(arg);

}

}
