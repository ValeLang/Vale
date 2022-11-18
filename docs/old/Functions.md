we should consider doing named arguments like swift.

in the end, i think its a bit extreme for a lot of cases, like range(0,
10) its pretty obvious. when there gets to be like 7 arguments, its
pretty necessary.

so, i propose we make it very easy to write a linter. it would be a
compiler plugin that has access to stage 0, and it could detect when
we're calling methods that have at least 3 arguments, and warn us to
name them.

for named parameters, we might have to support something in the function
signature to specify the external name and the local name.

range(from f: int, to t: int) {

\[f, t\]

}

but it means we cant use keywords for param names anymore. does 'virtual
f: int' mean an external name 'virtual', or that its using vtables?

perhaps we can use better keywords to deal with this. virtual -\> virt,
and override -\> spec.

or, we can allow someone to define the signature beforehand:

fn range(from: int, to: int) : \[int, int\];

fn range(f: int, t: int) : \[int, int\] = {

\[f, t\];

}

super annoying though.

maybe we can use symbols?

fn range('from f: int, 'to t: int) : \[int, int\] { ... }

range('from 5, 'to 8)

the difference between symbols and names is that names are only ever
local. symbols can be part of APIs? dunno, need a better distinction.
look up swift's distinction between external names and local names for
inspiration.

symbols cant be values? only used during compile time to match things?

theyre different from annotations in that they aren't "defined" in one
place.

## Functions

fn sum(a:int, b:int)int {

a + b

}

alternately, can use = and ;

func sum(a:int, b:int)int = a + b;

different than lambdas.

## Currying

sum(a:int, b:int)int { a + b } is called like sum(4, 5)

but we can also do:

sum(a:int)(b:int)int { a + b} and would be called like sum(4)(5) or sum
4 5

it basically makes it into a function that returns a function.

if(condition)(thenBody) { ... }

and we can take it even further and do this, but we shouldnt:

if(condition)(thenBody)else(elseBody) { ... }

because the other approach works much better and handles precedence and
can be extended to arbitrary number of elseifs.

## Template Arguments

no using template arguments as infix operators!

can use them inline like

doThings{(splark: #T)

but thats just shorthand for

doThings{:(T)(splark: #T)

rule: for a parameter, you can have one of these:

-   :#T

-   pattern and/or virtual

maybe someday in the future:

-   :#T(something, something)

maybe someday in the distant future, we can combine some of those in
more interesting ways.

### Parameter Packs

doThings(splark: #T) means splark is a single param of any type

doThings(splark:\...int) means splark is an array of int

doThings(splark:\...#T) means splark is an array of any type

doThings(splark:#\...T) means splark is an array of all sorts of things.

doThings(splark:#\...T) {

print(\...splark...)

}

doThings(4, "hello", true) manifests as

print(4, "hello", true)

doThings(splark:#\...T) {

{\...

print(splark\...);

}

}

manifests as

print(4);

print("hello");

print(true);

## Function Syntax

0\. { is always preceded by some sort of signature.

1\. all callable type declarations must have the return type, but
signatures dont have to.

2\. param lists may only be in parentheses

3\. () means param list

4\. void means empty tuple

5\. let x (something)(somethingb)(somethingc) = \... the last one is
always a tuple

because rule 1.

6\. func x(something)(somethingb)(somethingc) { the last one is always a
tuple because

if it was a parameter list, that would be a callable type, but it doesnt
have

a return type, so contradiction by rule 1. that leaves only that it is a
tuple.

it must be the return type to (somethingb).

## Function Signatures are Patterns

can function signatures be\... pattern matchers? thats basically what
they are, right?

## Lambdas

Two kinds, block lambdas and free lambdas.

Block lambdas look like:

-   { ... }

-   { ... \_ ... }

-   {(x) ... } equivalent to {(x: #T) ... } equivalent to {:(T)(x: #T)
    > ... }

Block lambdas:

-   Their owning pointer lives in the stack frame they were created.
    > Everyone else gets a borrow pointer.

-   Are limited to the lifetime of their parent function. (maybe we
    > should limit them to the lifetime of their expression?)

-   If you ret from them, it rets from the parent function, destroying
    > the stack all the way up.

-   On their own line are called immediately. Good for scope and stuff.

-   must be no space between { and (. if there is a space itll be
    > interpreted as an expr.

Free lambdas look like:

-   {\[\] ... }

-   {\[x\] ... \_ ... }

-   {\[&x\] ... }

-   {\[x, &y\](x) ... }

-   {\[x, &y\]:(T, R)(x: #T, y: #R)#R ... }

Free lambdas:

-   The result of this expression is an owning pointer.

-   If you ret from them, it rets from them alone.

-   must be no space between { and \[. if there is a space itll be
    > interpreted as a expr.

myList.foldLeftStartingWith(0){ \_ + \_ }

myList foldLeftStartingWith 0 +

That first one, the { \_ + \_ } is a block.

Templates:

-   {\[&x\]:(T)(a: #T) \...
