# Expressions

[[Expressions]{.underline}](#expressions)

> [[Built-in Operators]{.underline}](#built-in-operators)
>
> [[or]{.underline}](#or)
>
> [[and]{.underline}](#and)
>
> [[dot: "."]{.underline}](#dot-.)
>
> [[borrow: "&"]{.underline}](#borrow)
>
> [[borrow mutable: "&!"]{.underline}](#borrow-mutable)
>
> [[maybedot: ".?"]{.underline}](#maybedot-.)
>
> [[assumedot: ".??"]{.underline}](#assumedot-.)
>
> [[index: ".7" or ".(aNum)"]{.underline}](#index-.7-or-.anum)
>
> [[doubledotaccess:
> "myList..member"]{.underline}](#doubledotaccess-mylist..member)
>
> [[doubledotmap:
> "myList..func()"]{.underline}](#doubledotmap-mylist..func)
>
> [[doubledotstamp: "(..
> someStuff(myList..))"]{.underline}](#doubledotstamp-..-somestuffmylist..)
>
> [[tripledot: "(\...
> someStuff(myPack...))"]{.underline}](#tripledot-...-somestuffmypack)
>
> [[Precedence /
> Interpretation]{.underline}](#precedence-interpretation)
>
> [[STL Operators]{.underline}](#stl-operators)
>
> [[math]{.underline}](#math)
>
> [[bitwise or: "bor"]{.underline}](#_thblej8y3yuf)
>
> [[bitwise and: "band"]{.underline}](#_aa8ykjog88cv)
>
> [[bitwise xor: "bxor"]{.underline}](#_a36zpvbbc89e)
>
> [[equal: "=="]{.underline}](#_asomn4tfxql5)
>
> [[unary boolean not]{.underline}](#_svwkdcqsuzdf)
>
> [[ones complement: "\~"]{.underline}](#_tmcmyw6bt94t)
>
> [[not equal: "!="]{.underline}](#_ey3115qe49nt)
>
> [[less than: "\<"]{.underline}](#_9d7zp0qetjc0)
>
> [[greater than: "\>"]{.underline}](#_a8jr9l3t0qil)
>
> [[less than: "\<="]{.underline}](#_n8no3ih8384h)
>
> [[greater than "\>="]{.underline}](#_dc60i4g0eygu)
>
> [[bit shift left: "bshl"]{.underline}](#_3lwb8n4gt1bb)
>
> [[bit shift right: "bshr"]{.underline}](#_s4fx2lbg5j5)
>
> [[if statement]{.underline}](#if-statement)
>
> [[match statement]{.underline}](#match-statement)
>
> [[Functions as Operators]{.underline}](#functions-as-operators)
>
> [[If/Else]{.underline}](#_grpjsli8kxxz)
>
> [[Switch/Match]{.underline}](#_wqdw8couwtov)
>
> [[Let]{.underline}](#let)
>
> [[Things Next to Each Other Call Each
> Other]{.underline}](#things-next-to-each-other-call-each-other)

## Built-in Operators

### or

Does short-circuiting.

If the two operands are booleans, will return a boolean.

If the left side is an Option:T, and the right is type X, then the
result will be a (T\|X).

### and

Does short-circuiting.

If the two operands are booleans, will return a boolean.

If the left side is an Option:T, and the right is type X, then the
result will be a (T\|X).

### dot: "."

If the left side is a struct, and on the right is the name of a member
of that struct, then will access that member of the struct.

### borrow: "&"

### borrow mutable: "&!"

### maybedot: ".?"

.? is like ?. in swift.

### assumedot: ".??"

.?? will throw if its null

### index: ".7" or ".(aNum)"

.3 or .(some number here) will index into an array.

If this is in a mut context, like:

myArray.(myNum + 3) = "hello";

then this will be transformed into:

\_\_setAtIndex(myArray, myNum + 3, "hello")

Otherwise, like:

let x = myArray.(myNum + 10);

will be transformed into:

let x = \_\_getAtIndex(myArray, myNum + 10);

### doubledotaccess: "myList..member"

Is rewritten to:

myList.map({ \_.member })

### doubledotmap: "myList..func()"

If rewritten to:

myList.map({ \_.func() })

### doubledotstamp: "(.. someStuff(myList..))"

Inbetween the "(.." and ")" is the expression template.

Inside, if theres a variable with .. after it, it's interpreted as an
iterable.

Transformed to this:

myList.map({(elem) someStuff(elem)})

Above is equivalent to myList..someStuff()

these are all equivalent:

-   myList.map(p =\> invert(p\*4 + 5))

-   myList..{invert(\_\*4+5)}

-   (.. invert(myList..\*4 + 5))

### tripledot: "(\... someStuff(myPack...))"

expression: print(\...((myfunction(whatever)\...)+4))

and lets say myfunction returns (3, 4)

can be thought of as:

print(\...(((3, 4)\...)+7))

which unrolls to:

print(3 + 7, 4 + 7)

## Precedence / Interpretation

-   (looser)

-   binary function call starting with "=" or "!"

-   or

-   and

-   eq, neq

-   binary function call starting with "+" or "-"

-   binary function call starting with "\*" or "/" or "%"

-   regular binary function

-   unary function call, regular function call

-   methodcall, doubledotmethodcall, tripledotmethodcall

-   borrow, borrowmut

-   dot, doubledot, tripledot

-   parentheses

-   (tighter)

Test cases:

-   &!something.doFunc(4) -\> (&!something).doFunc(4)

-   &!something.member -\> &!(something.member)

-   -List(3, 6, 7).foldLeft(+) + "hi"

If things are rammed right up against each other, like the "-" and "4"
in "-4", then the thing on the left is considered a unary call. "x-7" is
interpreted as "x(-7)".

Generally, unary operators have much higher precedence than binary
operators.

## Constructs

### if statement

let x =

if {condition} {

somethingThatReturnsAnInt();

} else if {otherCondition} {

somethingThatReturnsABool();

} else {

somethingThatReturnsAString();

};

x is a (int\|bool\|string)

let x =

if {condition} {

somethingThatReturnsAnInt();

} else if {otherCondition} {

somethingThatReturnsABool();

};

x is a ?(int\|bool)

if an expression starts with an if, then it doesnt need a semicolon at
the end:

if {condition} {

doThings();

} else {

otherThings();

}

This can be implemented in the STL (except for the lack of semicolon)
but it should be implemented as a first-class construct of the compiler.
That way, we can do much better error messages.

### match statement

let mystuff:(bool\|int) =

match(myThing,

{(:Marine(hp, atk)) stuff},

{(:Firebat(hp, fuel)) stuff},

{(5) true},

{(a: Int) blorp},

{(\_) 0})

Every lambda has an \_\_apply and a \_\_canApply. Match can take in a
lambda, try its \_\_canApply, and if it works, call its \_\_apply.

If we can, lets also combine those and use an ETable\<interfaceID,
lambda\> to make things work.

### Let

let (pattern) = (expression)

= wont conflict with anything because:

-   we know when a pattern expression ends.

-   we only start expression context after we see the =

## STL Operators

### math

These are things that one would expect to be operators, but are in the
STL:

-   unary +

-   binary +

-   unary -

-   binary -

-   \*

-   /

-   \%

-   equal: "=="

-   not equal: "!="

-   bitwise or: "bor"

-   bitwise and: "band"

-   bitwise xor: "bxor"

-   bit shift left: "bshl"

-   bit shift right: "bshr"

-   bitwise ones complement: "\~"

-   unary boolean not: "not"

-   less than: "\<"

-   greater than: "\>"

-   less than: "\<="

-   greater than "\>="

-   square: "sq"

-   square root: "sqrt"

-   pow: "pow"

### Functions as Operators

Binary functions can be infix operators, unary functions can be prefix
operators.

~~solve the precedence problem: just require parentheses! ...except when
you have the same operator over and over in an expression. x + y + z is
fine, but x + y - z will need (x + y) - z, or if youre weird, you can
even do -(+(x, y), z)~~

~~put a compiler warning in if they try to do x + y \* z.~~

someFunc(&!myThing, 4)

is the same thing as:

&!myThing.someFunc(4)

fn plus(a, b) { a + b }

5 plus 6

this is definitely good.

the above only needs to get through the namifier stage to know what the
rough structure of an expression is.

the below two approaches need to be in the templar stage.

let plus = {(a, b) a + b};

5 plus 6

maybe. its still readable.

the above two mean you can parse expressions as repsep(rep(unary) +
data, binary).

the below one means you have no freakin clue.

5 {(a, b) a + b} 6

5 getOperator('\*') 6

bad:

-   makes it so you have to evaluate a ton of stuff before you can
    > figure out precedence.

-   much less readable

will need to define precedence somehow. scala rules are pretty nice.

some rules for sanity:

-   if at the beginning or end of an expression, cannot be a binary.

    -   if someone wants to do something like "+ compose \*" then they
        > can just do "compose(+, \*)". It must be in the expression on
        > its own.

how do we deal with operators like - which have unary and binary forms?

best option is to use whitespace as a disambiguator.

f - 1 is a binary. this would be -(f, 1)

f -1 is a unary. this would be f(-1)

the only time this could get awkward is if someone tries f-1. Maybe we
should make it a compiler error if there's no space before a binary?

but we should allow no space before a unary. inverse !-myThing should be
allowed... right? maybe not?

for unambiguous things like 'inverse' then having a space is fine.

inverse myMatrix

this allows us to use entire words as operators, and it also allows us
to do if {true} {4} because if is a unary function right there.

to parse these things, split the expression by binaries first, and then
everything unary is left to right.

-b + sqrt(pow(b, 2) - 4 \* a \* c) / (2 \* a)

splits into: -b, sqrt(b \^ 2 - 4 \* a \* c), (2a)

compares precedence of + and /, to make it into: +(-b, /(sqrt(b\^2 -
4\*a\*c), 2a))

b \^ 2 - 4 \* a \* c

splits into: b, 2, 4, a, c

compares precedence of \^ - \* and makes it into: -(\^(b, 2), \*(\*(4,
a), c))

if {true} {5} else {7}

else is the only binary, so splits into if {true} {5}, {7}

becomes: else(if {true} {5}, {7})

loose/scala style:

-   myVec \* 2 cross otherVec \* 3

-   (myVec cross otherVec) \* -1

tight style:

-   (myVec \* 2) cross (otherVec \* 3)

-   myVec cross otherVec \* -1

### Things Next to Each Other Call Each Other

myCallable 3

because of pack rules, is equivalent to

myCallable(3)

myFunctoryList map print

would be ambiguous, so we say that things on the left get priority.

notes:

Is there a way we can do a flat map, like unix does? unix is really good
at flatmapping, with its pipes. perhaps we just have to make a really
obvious class to use. instead of List we can have FlatList?

myFlatList..doThings()..y;

if doThings returns the same collection it took in, then that would work
well. if not...

myFlatList..doThings()..flatten()..y;

unix pipes are so nice. they automatically do flatmap. maybe we should
make a flatmap operator? its so useful!

wait... if doThings() doesnt take in a generic container, it's bad. the
STL should set a good example for how things can work with flatmaps.
scala does nicely here too.

also relevant: we can have a / operator that basically filters.

now all we need is an operator that does flatMap.
