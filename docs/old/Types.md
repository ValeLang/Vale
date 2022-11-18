# Types

[[Types]{.underline}](#types)

> [[Standard Types]{.underline}](#standard-types)
>
> [[No Arrays or Tuples, Only
> Sequences]{.underline}](#no-arrays-or-tuples-only-sequences)
>
> [[Maps]{.underline}](#maps)
>
> [[Vector]{.underline}](#vector)
>
> [[List]{.underline}](#list)
>
> [[Tuples Convertible to/from
> Structs]{.underline}](#tuples-convertible-tofrom-structs)
>
> [[Packs]{.underline}](#packs)
>
> [[Unions]{.underline}](#unions)
>
> [[Colons to Enter Type
> Context]{.underline}](#colons-to-enter-type-context)
>
> [[Variables]{.underline}](#variables)

## Spaces

Remember, all Templexes must know when they stop. They can\'t go on
forever like expressions can (with their infinite calling like
f()()()())

This is because the array sequence syntax is like \[4 int\], or \[N
int\] or \[N T\] which means two templexes are right next to each other.

## Standard Types

### No Arrays or Tuples, Only Sequences

lets combine the notions of tuple and the array

tuple is usually a sequence of differently typed things, not indexable

array is usually a sequence of things with the same type, indexable

but we can combine these.

start with the array.

let x be \[8, 6, 4, 3\];

let i be intFromStdin();

x.(i) is an int

now, let it have different types if it wants

let x be \[8, 6, true, 2\];

let i be intFromStdin();

x.(i) is an (int\|bool)

so this weird multi-typed array is basically a tuple, but indexing it
gives

a union. bam. done.

also, lets have \[4 int\] mean \[int, int, int, int\]

We do have a runtime-sized sequence, \[\* Int\]. means "some number of
ints, we dont know".

\[10 Int\] can be cast to \[\* Int\]. In fact, any uniform-element-type
sequence is automatically an array under the hood, so can be dealt with
like a \[\* Int\].

Can't change the size of a sequence at runtime. One should wrap in a Vec
or something for that.

### Maps

let myMap = map("abc" =\> 6, "xyz" =\> 10)

-\> transforms into tuples, so this is equivalent to:

let myMap = map(\["abc", 6\], \["xyz", 10\])

map is a function that makes the things.

myMap.("some key here")

this is an override of \_\_index

### Vector

Indexed by index, and resizeable.

### List

It's a list.

### Tuples Convertible to/from Structs

struct Moo {

a: int;

b: char;

c: bool;

};

Moo moo = { 4, 'c', true };

totup moo; // gives \[4, 'c', true\]

### Packs

(((4, 3), 2, (7), 9, 9, 2) is equivalent to (4, 3, 2, 7, 9, 9, 2)

\(10\) is equivalent to 10

() is equivalent to void

functions only take in packs

### Unions

let x: (int\|bool) = 5;

When an int or bool or anything tiny is in a union, it's wrapped in a
struct, like java's boxing.

Lots of ETables will be used when unions are involved.

Can unions be override params?

### Colons to Enter Type Context

sum (a:#T, b:#T) #T { a + b }

called by:

sum(3, 4)

or

sum:float(3, 4)

or

sum:(float)(3, 4)

: is the way to switch from an expression context to a type context.

## Variables

Variables that can be changed to point at something else should end in !

let x! = 4;

then they can be changed with mut.

mut x! = 10;

This never changes the thing we used to point to, it only makes us point
at something new.

Under the hood, yes, it's changing the value in memory, but
conceptually, x is a changeable reference to a constant int there.

mut myOwning = mut myBorrow = MyClass!()

if the right side has an owning pointer, and the left side has a borrow
pointer, then the result of the operation is the owning pointer.

Then again, it's so easy to hide it. a final reference to a mutable
vector, for example. Unless we say that anything referring to anything
containing anything mutable must end in "!" ?
