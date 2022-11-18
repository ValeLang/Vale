[[VLang]{.underline}](#_32r29we8vmqq)

> [[Type Syntax]{.underline}](#type-syntax)
>
> [[No Arrays, Only Tuples]{.underline}](#no-arrays-only-tuples)
>
> [[Packs]{.underline}](#packs)
>
> [[Colons to Enter Type
> Context]{.underline}](#colons-to-enter-type-context)
>
> [[Switching from Expression to Type
> Context]{.underline}](#switching-from-expression-to-type-context)
>
> [[Currying]{.underline}](#currying)
>
> [[Template Arguments]{.underline}](#template-arguments)
>
> [[No Using Template Arguments as Infix
> Operators]{.underline}](#no-using-template-arguments-as-infix-operators)
>
> [[Lambdas]{.underline}](#lambdas)
>
> [[Symbols in front of
> Blocks]{.underline}](#symbols-in-front-of-blocks)
>
> [[Params inside Braces]{.underline}](#params-inside-braces)
>
> [[Typecasting]{.underline}](#typecasting)
>
> [[Dot Operator]{.underline}](#dot-operator)
>
> [[Methods]{.underline}](#methods)
>
> [[more]{.underline}](#more)
>
> [[Map/Unroll Operator]{.underline}](#mapunroll-operator)
>
> [[Switch/Match]{.underline}](#switchmatch)
>
> [[Implementation]{.underline}](#implementation)
>
> [[Uncategorized]{.underline}](#uncategorized-1)

# VLang

## To Do

template args, no runtime args: myFunction(type T)() { ... }

template args, runtime args: myFunction(type T)(int x) { ... }

no template args, runtime args: myFunction(int x) { ... }

no template args, no runtime args: myFunction() { ... }

so, if there's only one set of parentheses, its assumed to be the
runtime args.

template args and runtime args should probably always be in separate
parentheses / separate curries.

template args, no runtime args, some empty currying: myFunction(type
T)()() { ... }

template args, runtime args, some empty currying: myFunction(type T)(int
x)() { ... }

no template args, runtime args, some empty currying: myFunction(int x)()
{ ... }

no template args, no runtime args, some empty currying: myFunction()() {
... }

the third and fourth ones look ambiguous:

-   myFunction(int x)() { ... }

-   myFunction()() { ... }

the fourth one actually isnt because in templating the args are
implicit, () doesnt make sense.

so... in templating, we'll always have an arg, which means it will
always have a type...

which means we just need special templating types.

myFunction(T: Type, E: \...Type)()

but i dont want to use "Type" as a keyword. ick. maybe typ?

myFunction(T: typ, E: \...typ)()

gross.

put in some errors for when we forget override and virtual

a statement that begins with =(whitespace) could be how to return things
locally. maybe ret could get out of the function early? or we could even
call it earlyreturn to make it super annoying, that could work

post to reddit about the awkward way we often transform variables and
function calls into objects, like in menu systems. is there a better
way?

use vtables for members as well as methods! credit to jondgoodwin

interface I:T {}

struct S implements I:Int, I:Bool {}

fn doThing:T(virtual I:T) {}

fn main() { doThing(S()); }

what got stamped? doThing(:I:Int)? or doThing(:I:Bool)?

\...borrow refs can totally just use the same int as the owner count O_o

so... handing a immutable object in where a const borrow is expected is
totally fine! whoaaa

fun fact: syntax highlighting only needs to get through the templar
scanning phase!

\...as long as we dont allow local variables to be usable as infix
functions. which i think we already disallow so good.

another fun fact: we can split the unscrambling into a separate phase in
the beginnings of the templar, when we know things\' banners. the
banners we know after just the templar scanning phase so thats pretty
nice.

get rid of \_\_addFloatInt, do proper upgrading of integral instead. or
be like Go, and require explicit casting between all these things.

make if statements return optionals instead of making defaults

our stack frame structs might lead to some rather explosive stack
growths. for example:

get rid of block, theres no such thing. but, its hard, especially when
you have to expand an expression from stage1 into multiple expressions
from stage 2. this happens when we compile a stage 1 "let (x, y) = (4,
5)" into its corresponding multiple stage 2 lets.

rename namifier to scout?

is Nothing a subclass of packs? or perhaps Anything? should we make
interface functions which dont specify a return type return Anything?
that way, subclasses can do their covariant return type thing.

are packs covariant-able with shorter packs? could we override a
function to return more via packs? that'd be kinda cool...

\...shit, covariant return types mean having different families for each
level. craaap.

make sure free lambdas dont see the vars in their parent scope unless
listed

what happens if we, like\...

fn x(a: int) { 3 }

fn main() {

let x = {(a: bool) 5};

x(5);

}

should we make that into an overload? right now that local x just nukes
everything

fn main() { let x = 4; {x}() }

calling a lambda means extracting two things from it: the function
pointer, and the closure struct. to extract two things from something,
it needs to be in a variable (otherwise we're copying the source
expression twice, which is weird). that variable can either be a local
or... we can make a generic

inline fn \_\_callLambda:(lambda: &ICallableStruct, args: #\...T) {

lambda.\_\_function(lambda.\_\_closureVars, (\...args...))

}

\...probably better to just store it in a local variable.

## Semicolons

We might not need semicolons. We can use the following rules:

-   If a newline is after a binary operator, then consume the following
    > line as part of this one.

-   If a newline is before a binary operator, or after a binary
    > operator, then consume the previous line as part of this one

-   Consider the = in let and mut to act like binary operators like the
    > above lines.

-   Otherwise, a newline separates lines.

-   If a newline is inside this line's subexpression, like ( ) \[ \] { }
    > then it doesn't split this line.

This way, we can do if statements without the semicolons!

let x =

if {a \< 5} {

} else {

}

println x

but, this means . will have to be on the preceding line, which is icky.

## Ampersand

Means "lend".

Assignments otherwise just do moves.

## Security

private: invisible outside of this file

constprivate: const outside of this file

visiblefortesting?

## Uncategorized

patterns can have template structs inside them, so it has to come in or
after stage 2

a bunch of functions that share signatures but different pattern code
will be put into

a single dispatcher function, which calls into many helper functions.

the dispatcher function will throw if it couldnt apply.

in the future, we should replace this with some pretty aggressive
inlining and/or returning bools.

copies:

-   pure things can just be reference counted.

-   non-pure things must be actually copied

when doing a copy, non-shared owning pointers are duplicated

owning pointers \*can\* form cycles which is worrisome\... what do we do
when copy() is called on those?

[[https://docs.google.com/spreadsheets/d/1McoQl1MUza6blVLWZqoMH5ATO058FbKdTgMAHb07yD8/edit#gid=0]{.underline}](https://docs.google.com/spreadsheets/d/1McoQl1MUza6blVLWZqoMH5ATO058FbKdTgMAHb07yD8/edit#gid=0)

H = heap, as opposed to stack

SP = need a pointer on the stack, because we'll be pointing somewhere
else

H if:

\- large

\- if \$ moved, then need SP

\- small, moved with outstanding borrows

\- if \$, then need SP

otherwise, stack with no SP.

without small move-\>copy optimization:

H if:

\- large,

\- small, moved.

H SP if \$ and moved.

(nice, the outstanding borrows thing fell away)

with everything being assumed \$:

H if:

\- large,

\- small, moved

H SP if moved

(only factors now are small-vs-large and moved)

with everything being assumed large:

everything is H.

H SP if moved.

(only factors now are whether it's moved)

with everything being assumed moved:

everything is H SP.

Pointer2(nullable, mutable, owning\|borrow\|weak, shared, heap,
stackpointer, innerType)

Type2(): calculate small,

owning, shared basically shared_ptr

owning, not shared basically unique_ptr

borrow, shared borrow of shared

borrow, not shared borrow of unique

weak, shared weak of shared

weak, not shared weak of unique

every object has a ledger.

struct Ledger {

int strongPointers;

int borrowPointers;

int weakPointers;

}

goOutOfScope() {

switch (ownership) {

case

when a weak goes out of scope, decrement the object's weak count. if
strong == 0 and weak == 0, delete the ledger.

when a borrow goes out of scope, decrement the object's borrow count.

when a strong goes out of scope:

-   if borrow != 0, crash

-   if strong == 0 and weak == 0, delete ledger

-   if strong == 0

    -   if heap, delete object

if i do a move into a function that wants to borrow\... isnt that fine?
just make it go out of scope

after the call returns?

if i do a lend into a function that wants ownership though, thats bad.

for blocks, as an optimization we can make it put all the closured
locals into a struct, and just pass around borrowpointers to that. we
wont be creating a new closure instance every single time we get to the
closure. only drawback is that we're allocating space for all the locals
up-front. but doesnt C do that anyway? this saves a bunch of allocas.

actually, we might have to do this, if we want to be able to mut from
inside a block...

and, lets disallow mut of a free lambda's closured var. basically make
them the equivalent of let x, not let \$x.

you know, this is the equivalent of just using boxes. that struct is
basically a box for multiple variables. a multibox, if you will.

so what about when we have nested blocks? do we have to do the
coordinate thing... ugh... isnt that more complicated than just having
triple pointers?

pattern matching makes a lot of new variables which is cool... but can
we get around that by returning a pack from a special
pattern-matchy-extracty function? that might also make the generated
code more readable. wonder how bad it would be for optimization? i
suppose it can be inlined really easily, then optimized

this approach, where we make a function instead of making new local
variables, is probably a good one. we should also use it where we call a
closure.

MoveSoftLoad can be transformed into the proper dereferences and
exception throws by Midas, thats fine, because thats a runtime check.
compile time checks should really be in the templar.

speaking of which, we should split the templar... one part that just
takes the namified stuff and assigns types to it, and one part that
transforms patterns, lifts functions, etc. editors can take the output
of the typed stuff.

fn call:T(callable: T) {

}

\# means owning

& means borrowing

leaving it off means borrowing or owning, whatev

split softload into moveload and lendload. they have different
resulttypes.

otherwise, we\'re making two owning pointers everywhere, which will
cause double frees.

look specifically at the templar output for fn main() { let (x, y) = (4,
5); y }

its patterns are making owning pointers from other owning pointers with
softloads.

if a variable is moved away, from inside a closure\... what happens\...?
runtime error, yes

// those are owning references, and thats an owning pack\... so m and g
should be owning

let (m, g) = (Marine(), Goblin())

// the input there is an owning reference, and it owns hp and atk as
well.

let Player(hp, atk) = Player()

options:

\- let the ether own Player, and hp and attack can be borrow references.

\- let the ether own Player, and hp and attack can be Addressibles,

in other words, hp and atk are basically aliases for a StructLookup3
expr, not variables themselves.

this would mean we could do a mut hp = 6 which might be surprising\...
perhaps disallow muts?

but, it would let us move or lend as we see fit.

\- let the Player be destroyed, and hp and attack are owning references,
harvested.

let player = Player()

let Player(hp, atk) = &player

// in that case, hp and atk should definitely be borrows.

let p: Player(hp, atk) = Player()

// p would be an owning reference, and hp and attack would be borrow
references

let MyStruct(hp, loc: InnerStruct(x, y)) = makeAMyStruct();

// MyStruct would be destroyed

// hp owning

// loc owning

// x and y are borrow

let MyStruct(hp, InnerStruct(x, y)) = makeAMyStruct();

// MyStruct and InnerStruct would be destroyed

// hp, x, and y are owned

given an owning, capture produces an owning and gives downwards a
borrow.

given a borrow, capture produces a borrow and gives downwards a borrow

given an owning, pack destroys, produces nothing, and gives downwards
owning pointers

given a borrow, pack produces nothing, and gives downwards borrow
pointers. IS THIS EVEN POSSIBLE?

given an owning, struct destroys, produces nothing, and gives downwards
owning pointers

given a borrow, struct does nothing, and gives downwards owning pointers

rust:

let child = Command::new(\"/bin/cat\")

.arg(\"rusty-ideas.txt\")

.current_dir(\"/Users/aturon\")

.stdout(Stdio::piped())

.spawn();

we can do the same thing with the idea where we can seal a class, if
we're certain that theres no outstanding borrows. also, the above is a
case where we had a move of 'this'.

by default addressibles become const owning pointers when used

& turns an addressible into a const borrow pointer

\$ turns an addressible into a mutable owning pointer

&\$ and \$& turn an addressible into a mutable borrow pointer

if {blark} then {

} else {

}

perhaps, if something is a let, which means it cant change, then we dont
have to pass \*\* into closures for it. could help the borrow checker?

map syntax:

\[

can we mark functions as nondeterministic? and then, when our core
deterministic program calls into these nondeterministic functions, it
records in a snapshot. when we run it in debug build, it replays all
them.

any function that calls a nondeterministic function is itself a
nondeterministic function. \...and if a virtual function is
nondeterministic, then all of its ancestors have to be deterministic...

if something takes in a struct that has a pointer to a clock, then
should that be nondeterministic? no, because they havent called gettime
on it. any function that calls gettime is nondeterministic. its not the
clock itself, but the function that is called with it which is
nondeterministic.

, can be a "pack cons" operator. that way we can do:

switch (myDude) {

{:Marine(hp, atk) println("marine!")},

{(g: Goblin) println("goblin!")}

}

\^ look at dat brace, thats what we get for considering , to be an
operator.

we could even consider ; to be an operator which just throws away the
thing to the left.

I want to be able to compile match statements like this:

myCar match {

case Honda(color) =\> print \"hi\";

case Toyota(gas) =\> print \"world\";

}

into virtual function calls. perhaps this can just subclass a visitor?

can do symbols with backtick \`. or perhaps those are just one-word
strings? thats a cool idea...

{( )} nope nevermind, thats hard to distinguish from a param lambda

\[( )\]

(( ))

duuuuude if we use the colon in expression context, then we can do map
syntax! then again, not much advantage over =\>.

map(4: 6, 9: 10)

ListNode:Honda {

\- head: Honda

\- tail: ListNode:Civic

\- head: Civic

\- tail: Nil

}

ListNode:T {

value: T

}

have a ListNode:Honda pointer to a ListNode:Civic

i try to say .value on it, i expect a Honda but i get a civic

i need to check it at runtime and upcast it then, CRAP

i think this is just a problem in struct members

so when we get something out of a struct, thats when we would check it,
i think

and we\'d only check it if it was a superclass.

actually, we might not even have superclasses, so it might just be for
interface pointers.

and, its only on immutable structs, too. so, pulling a supertype from an
immutable.

in a pattern, after the :, theres only constants and types. used to
think any nontype could be there, but nope, constants.

\...and lambdas, maybe?

struct Marine { hp: Int }

let m = Marine(9)

match m {

{:Marine(hp: {\_ % 2 == 0})

hp / 2

},

{:Marine(hp)

hp

},

}

lets use : for named parameters?

fn doSomething(blarg: Bool) { ... }

doSomething(blarg: false)

it means we cant let the user overload :

what else can we use it for? maps mayhaps?

(("blarg": 5, "flarg": 9))

all hail the almighty colon

(("blarg", "flarg")) is a list

(()) is either an empty map or an empty list. can we make Nil usable by
both map and list? yes! we can! a List:Nothing is technically also a
List:(key, value) which can be used in a map. cool.

ok, so we have it do named packs, maps, and lists. nice. not sure it's
better than the scala way though.

immutable

owned/lent/weak

shared/notshared

immutable = shared = struct. Marine

mutable = object = class

\- owned const: no such thing.

\- owned nonconst: Marine!

\- lent const: &Marine

\- lent nonconst: &Marine!

\- weak const: &&Marine

\- weak nonconst: &&Marine!

let m = Marine!()

let m = !Marine()

wait, in a pattern, after the :, theres only constants and types

upcasting should return that type

downcasting should return an option of that type

// someday:

// rename midas to hammer, and sculptor to midas

// need to know types before we lift lambdas into global scope

// because the lambdas need to know what type their closure struct
should be

// Stage 0:

// Inbetween (namifier):

// - Split identifiers. Make \"4+3\" into \"4\",\"+\",\"3\".

// - Add template type parameters for lambdas\' type-less params

// - Add names to functions

// - Figure out how many magic params a lambda has

// Stage 1: templated, untyped, scrambled, lambdas, patterns

// Inbetween (templar)

// - 1a. Figure out types

// - Depends on 0a

// - Depends on 1b because some things will be the result of templated
functions

// - Depends on 1c because we need to know the value of variables that
are the results

// of scrambled expressions

// - 1b. Stamp templates, unroll repeaters

// - Depends on 1a because we need to know the types that are being
applied to

// know when we need a new stamp

// - 1c. Unscramble expressions

// - Depends on 1a because we need to know the types of callables to
know what is

// an argument and what is a function

// - 1d. Turn patterns into code.

// - Hoist lambdas to global scope

// - Figure out closure coordinates for variables

// - Turn free lambdas into currys

// - Split currys into functions and structs

// - combine functions with different patterns but same resulting
signatures.

// Stage 3: untemplated, typed, unscrambled, functions. basically C.

// blocks are only valid for their expression. after that, shit goes
down. the reason is that

// we dont want to worry about some things inside the current block
going out of scope.

// besides, blocks are basically functions\... if we let this live
outside of its current block,

// things get a little weird.

// every lambda has as a secret first parameter, which is just a pointer
to the current

// stack frame. to go up multiple jumps, just keep looking at the first
parameter, then that

// thing\'s first parameter, and so on.

// lets carry the parentheses and packs and stuff through into the
templar, so it can make those

// have higher precedence over unary and binary operators.

also, so we can make immutabase models in multiple files, lets declare
things like this:

chronobase:Game struct Game { ... }

chronobase:Game struct Marine { ... }

Better than C++ for obvious reasons.

Better than Rust because

-   no retarded strict arbitrary reference rules

-   better graph support (immutabase, built in arenas, bounded
    > mark-sweep space?). must figure this out.

Better than Scala because of optimization:

-   No garbage collection

-   Immutabase

make a good way to freeze an object

how do you add an object to an immutabase?

"custom generated hash"\... we can have some sort of argument to a
hashing function that changes the hashing algorithm? and we can
calculate that thing at compile time, so we can guarantee no
collisions!?!?!

we can use ::

classes should have a "const except to" thing, where they can designate
certain classes or files or packages that can modify them. would be nice
for the warden. everyone can hand around a modifiable reference, but in
the end only a warden can modify it.

maybe we can use this syntax to select an overload?

let signature = doThing(:MyStruct)

how do we select one that has no parameters though? doThing(:)? no...
need something better.

perhaps we dont have to look at whether the last statement has a
semicolon

perhaps we can do what visual basic did, and have functions either be
called fn or sub

fn has return, sub doesnt

## Type Syntax

ids = for(units)(a: Unit){

\...

= a.id;

}

ids = for(units)(a: Unit)int = a.id;

ids = for(units)getId;

ids = for(range(0, n))(int) = rand()

ids = for range(0, n) (int) = rand()

ids = for range(0, n) (\_) = rand()

ids = for(range(0, n))(int){

\...

= rand()

}

is the (int) naming a tuple or a function parameter list?

not ambiguous because of 0, { is always preceded by some sort of
signature,

and signatures always start with a parameter list.

if we wanted a something that returned a tuple, i suppose it would have

been ():int{ \... }

for(unitsById)(id: int, Unit)(int, int, int) = doSomething

is each iteration returning a callable taking (int, int, int) that
returns void

or is each iteration returning a tuple of (int, int, int)?

the latter because of rule 1.

if we accidentally get one as a (unpackMe\...) thats fine because

thats past the syntax stage

let f = ()(){ \... }

does that mean f is a ():void?

or does it mean that f is a ():():void?

the latter, because of rule 3, () means param list.

how we declare string:void:Tile?

because rule 2,

(string)(void)Tile or (string)(void)(Tile).

in signatures, the input tuple is always in parentheses, and

names are optional but will have a colon. either (u: Unit) or (Unit)

## No Arrays, Only Tuples

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

## Packs

the justification for packs\' existence:

we need some way to send inputs to

inverse((a))

inverse(a)

dot(a, b)

dot getMyTwoVecs()

the idea that getMyTwoVecs returns two things that can be consumed by
another

function. we cant do that with sequences because those could be consumed
as

a single argument.

we could do something like dot.callWithArgs(getMyTwoVecs())

but its a little nicer to have some sort of special flat-ish structure

it has to be flat because inverse((a)) should be interpreted as
inverse(a)

since we can\'t do any operations with the pack itself, then there\'s
not much

reason to even have it be named. for that purpose, lets use seqs.

why have arrays/tuples in a world with packs? because we can\'t do any
operations

on packs because if its a 1-element pack, is that operation being done
on the

pack or on the element? len((myarr)) is 1 or len(myarr)?

## Colons to Enter Type Context

sum (a:#T, b:#T) #T { a + b }

how do we handle both these cases?

sum(3, 4)

sum(float)(3, 4)

we cant look at the thing in there, because that could be a constructor,
which can

be either a value or a type depending on its context. so we need the
context to

tell it what to be.

sum\[float\](3, 4)

in which case, we can\'t use \[2, 3, 4, 5\] as an automatic sequence
maker

we need some nice syntax for

map(\[3, 10\], \[4, 20\], \[5, 30\])

maybe map(entry(3, 10), entry(4, 20), entry(5, 30))

maybe map(seq(3, 10), seq(4, 20), seq(5, 30))

or some special syntax rules to support it?

what if we do like rust did

sum!float(3, 4)

sum!(int)(3, 4)

or better yet,

sum:float(3, 4)

sum:(int)(3, 4)

yes that, i like that. it fits nicely with the : being usually for
\"specifying types\"!

### Switching from Expression to Type Context

an important assumption made elsewhere is that we either are in a code
context or

a type context. thats the difference between

let a: MyClass be somefunc()

and

let a be MyClass()

in one case, saying MyClass is refers to the type, in the other case,
saying it

refers to the constructor

dont know if itll ever be useful, but if we ever really need to go from
value

context to type context, we could say (:MyClass)

since : seems to universally be the \"im about to talk about types\"
operator

## Currying

whoa. you could make something like... (listA
compareAll(thingComparator) listB)

which is equivalent to compareAll(thingComparator)(listA, listB)

compare(thingComparator) makes a comparifier, which when called will
compare two thingies

could be useful for == with floats, to specify epsilon.

(floatA ==(.001) floatB)

compareAll(comparator: #Comparator)(a: List:#T, b: List:#T) int {

for (...stuff) {

if let difference = comparator.compare(a, b) {

ret difference

}

}

ret 0

}

when looking at this, it makes you wonder, what if functors were the top
level citizen, instead of functions? would make implementing lambdas
pretty easy...

so, that thingy there, what's the type of compare(thingComparator)? can
we pass it around? if this was in c++ world, it would need to be
templated like crazy, no room for virtual in there.

## Template Arguments

### No Using Template Arguments as Infix Operators

no using template arguments as infix operators!

doThings template(R, T\...) (splark: (T\...)R) {

return 5 splark 7;

}

becomes invalid if we do:

bork(a: int)bool { a \< 6 }

doThings(int)(bork)

because itll manifest as:

doThings(splark: (int)bool) {

return 5 splark 7;

}

which is invalid because splark takes one argument so cant be used infix

instead, enforce that we never have infix calling. must instead:

doThings template(R, T\...) (splark: (T\...)R) {

return splark(5, 7);

}

and now that we dont have infix calling, combine becomes way simpler,

since we dont have to know how many parameters something takes in to be

able to finalify it.

lets try this out with array:

doThings template(R, T\...) (splark: (T\...)R) {

return splark(5, 7);

}

let arr be doThings(array(int))

manifests as:

doThings(splark: (int\...)array(int)) {

return splark(5, 7)

}

\...which works, nice

\...wait, no it doesnt. the T\... in the template thing said that there
could

be any number of template arguments, but there\'s only one in this case,
the int.

we need a different way to express that we\'re sending in many values of
one type,

or many values of many types.

how about doThings template(T) (\...splark: T) means many values of T,
referred to by splark

and doThings template(T\...) (splark: T\...) means splark is a parameter
pack of whatever the Ts are

and for example,

doThings template(T) (\...splark: T)

let b be doThings(5, 6, 7, 8)

and

doThings template(T\...) (splark: array(T)\...) { stuff here }

let b be doThings(array(int)(3, 4, 5), array(bool)(true, false),
array(int)(\"hi\", \"k\"))

and this just doesnt make sense and is invalid:

doThings template(T\...) (\...splark: array(T)\...)

because the latter \... already made this into a variable argument
function so we dont need the \... before splark

also, callable type declarations will be like:

(T\...)bool which means it takes in god knows what

(\...T)bool which means it takes in however many Ts

what if instead of saying up front template(T) we just put \# in front?

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

## Lambdas

Two kinds, lambdas and blocks.

myList foldLeftStartingWith 0 +

That first one, the { \_ + \_ } is a block.

if (x) {

}

myFunctorList map print

would be ambiguous

we have three options here:

-   try different combinations until one makes sense. too hard to think
    > about though, dont like this one

-   binary operators get priority.

-   things on the left get priority.

going with the left thing.

with our rules this would become myFunctorList(map(4)) and syntax error

theyd have to rewrite it as

map(myFunctorList, print)

or

myFunctorList.map(print)

maybe we can use this to handle the ambiguity for if statements?

if (x) {

}

the (x) would belong to the if, rather than to the { }

yes, i like this, because it gives complete control over the entire
statement to the first thing, if it wants to. very good for things like
if.

for would need this though:

for (myList) ((x){

})

or

for(myList, (x){

})

lambdas are ambiguous with tuples.

\[\](){ \... }

wait, no they arent. just see if theres a { somewhere to the right of
it.

current rules say that the thing to the left tightly grabs the thing to
the right

but lambdas are (){\...} which means they grab the param list thinking
it's a pack or something.

we need some way to tightly bind together the param list to the body.

### Symbols in front of Blocks

put a symbol in front of blocks

> for myList lambda(x){ ... }
>
> in more general terms, we need a way to switch to pattern context, and
> pattern + block = lambda.
>
> more specifically, we need to put something in front of it when theres
> a param list. just having a block is fine.
>
> for myList pat(x){ ... }

if (x) { ... } is easy, its just a block.

for myList { print \_ } is easy, its just a block

for myList \[\]{ print \_ } is a lambda. its the \[\] that makes it a
lambda.

lam\[\]{ print \_ }

the \[\] is optional, which makes it a free radical

the () is optional, because params can just be \_

so we need another signal to bind the () and the \[\] to the {}

but if neither of those are present... then do we not need it?

if x { ... }

for myList { let x = \_;

\...

}

if (x) lam{

}

for (myList) lam(x){

}

for can be implemented:

for template(T) (list: List:T) template(C) (callable: C) = list.map(C)

Suddenly you can do this:

let results =

for (myList) (x: Int) {

x + 4

}

though, return gets tricky. we would be tempted to use return in there.
perhaps we should make a rule saying ret will always exit the topmost
function? hmmm...

how about this. (x) { } is what we call a block, it's a special kind of
lambda, and its scope is limited to the scope of its parent, it must be
called within the lifetime of its parent. same with {\_ + \_} thats a
block. returning from blocks will return from their parent function.

but, for a legit closure that we want to stick around, lets require the
\[\] in front, like \[\](x) { }

i kind of like that, because then we force the programmer to think about
what theyre pulling in.

print template(T\...) (args: T\...) {

for (let x in args) {

print(x)

}

}

omg and it can basically be a map thing!

// makes (4, true, 6) into (list(int)(4), list(bool)(true),
list(int)(6))

listify template(T\...) (args: T\...) {

ret for (let x in args) { list(T)(x) }

}

perhaps even:

listify template(T...) (args: T...) {

ret for(args) { list(T)(\_) }

}

breaks and continues are tricky though.

for template(T) (list: List:T) template(C) (callable: C) = list.map(C)

lets turn that into something without map real quick...

for template(T) (list: List:T) template(C) (callable: C) {

using R = :typeof(C(T));

mut range = list.range();

mut result = List:R()

while (range.size()) {

mut thing = callable(range.front())

\$result.push(move(\$thing))

\$range.advance()

}

ret result

}

to allow for break and continue we'd need:

for template(T) (list: List:T) template(C) (callable: C) {

using R = :typeof(C(T));

mut range = list.range();

mut result = List:R()

\_\_while (range.size()) {

mut thing:(R\|Break\|Continue) = callable(range.front())

match thing {

(R){

> \$result.push(move(\$thing))
>
> \$range.advance()
>
> }
>
> (Break){
>
> \$range.collapse()
>
> }
>
> (Continue){
>
> \$range.advance()
>
> }
>
> }

}

ret result

}

the statements break; continue;

\_\_while wouldnt support break and continue but we could make a v.while
that does

\...this way lies madness

lets instead make a version of for that takes in a lambda with 3 params,
element break and continue which unroll the stack to a predetermined
marker. heck, return would probably use this same thing anyway...

we can call it \_\_beacon

for(items: List:#T)(callback: (#T)#R) = items.map(callback)

// #B is for the break

for(items: List:#T)(callback: (#T, #B)#R) {

mut results = List:#R()

mut range = items.range()

while (range.size()) lam{

\_\_beacon(

> lam(break){

mut result = callback(range.front(), break);

results.push(result);

> },
>
> lam{
>
> range = range.collapsed();
>
> })

}

results

}

usage:

for (myList) lam(item, break) {

if (item \< 3) lam{ break(); };

item \* 4

}

// #B is for the break, #C is for continue

for(items: List:#T)(callback: (#T, #B, #C)#R) {

mut results = List:#R()

mut range = items.range()

while lam{range.size()} lam{

\_\_beacon(

> lam(break){
>
> \_\_beacon(
>
> lam(continue){
>
> mut result = callback(range.front(), break, continue);
>
> results.push(result);
>
> range = range.advance();
>
> },
>
> lam{
>
> range = range.advance();
>
> });
>
> },
>
> lam{
>
> range = range.collapsed();
>
> });

}

results

}

usage:

for (myList) lam(item, break, continue) {

if (item \< 3) lam{ break(); };

if (item \< 5) lam{ continue(); };

item \* 4

}

loopInner(callback: (#B, #C)(#\...R), results: &List:\[#\...R\], break:
()void) {

\_\_beacon(

lam(continue){ results.push(callback(break, continue)); },

lam{});

}

loopInner(callback, break); // Tail call optimized

}

loop(callback: (#B, #C)(#\...R)) {

let results = List:\[#\...R\]()

\_\_beacon(

lam(break){ loopInner(callback, &results, break) },

> lam{});

results

}

if(condition: ()bool)(body: ()#T) {

\_\_if(condition, body)

}

while(condition: ()bool)(callback: (#B, #C)#R) {

loop lam(break, continue){

if (!condition()) break;

callback(break, continue)

}

}

for(items: List:#T)(callback: (#T, #B, #C)#R) {

mut range = items.range()

while lam{range.size() \> 0} lam(break, continue){

let result = callback(range.front(), break, continue);

range = range.advance();

result

}

}

usage:

let results =

> for (myList) lam(item, break, continue) {
>
> if (item \< 3) lam{ break(); };
>
> if (item \< 5) lam{ continue(); };
>
> item \* 4
>
> };

if {condition} { }

ifelse {condition} { } { }

ifelse {condition} {

} ifelse {condition} {

} {

}

perfect. if, when it expects an else body, it finds instead an 'ifelse'
symbol, it will know to continue.

if({condition}, { }, { });

if {condition} {

},

else {

};

what if the {} super aggressively claimed the parens to its left

well, then this wouldnt work: if (condition) { }

### Params inside Braces

put the params inside the braces

> {(a: Int, b: Str)
>
> in fact, swift does this.
>
> for myList {(elem)\
> \
> }

both solutions are still missing only one thing: if let. we cant declare
a variable in one lambda and have it used elsewhere.

\...wait...

if (let myMarine = myUnit:Marine) {

can become\...

if {myUnit:Marine} {(myMarine)

maybe we could also make if do:

if (myUnit) {(myMarine:Marine)

while (let nextMessage = getNext()) {

can become...

while {getNext()} {(nextMessage)

though thats where we would have had break and stuff.

if we had to pick an order: break, continue, nextMessage.

while {getNext()} {(\_, \_, nextMessage)

wow. it works.

what about closing, maybe .\[\]?

addEventListener({\[myVar\](event)

handle stuff here

})

## Typecasting

instead of .as: and .is: lets just have .as: which returns a nullable

in fact, lets not have .as:, lets just have :

for example, theUnit:Marine is a Marine\|Null

## Dot Operator

. is reserved, which means we have free reign over whatever follows .s,
just like... read whats after them

.? can be like ?. in swift

if we use .? maybe we should make a rule that names cant start with ?
cuz then .?something is weird if theres a var named ?something

.! can be something that throws if its null. or maybe .?? instead

if we do methods, then theUnit:Marine.?marineDance() is a thing

and this .\[ \], could be the way to index into an array or a list or
something, and .( ) can be something else, perhaps reflection?

nevermind, .( ) can do the same thing as .\[ \]. strings can do
reflection, ints can do elements

still open for use: .: .{ } .. ...

idea: foo..bar is the same as (\*foo).bar but we dont really do pointers
soooo\...

### Methods

i think we should have methods. but lets by default think of functions
as top-level things, and the . notation as just a shortcut to find
things that take the left thing as the first argument.

should myThing.myMethod result in a curried function? O_o then you could
pass it around, and not need binding? crazy... that would make
implementing it a bit easier too

## more

right now nothing uses {} or \[\] in expressions, will make it easy to
make lambdas

lend is a construct

make a special constructor type, lets say { } which takes in things like
the implicit xml stuff

const types are implicitly copyable? and cant have constructors or
destructors

int

string

if we make a plugin, there should be a keyboard shortcut which when you
hold it down

it shows the types of all the variables on the screen

thought experiment

lets assume that flagsArr is a pack

\... which is weird, because packs should never be in variables\...

but lets think anyway

parseFlags(args: array(string), \...flagsArr: str) {

let mut flags be toTMap(flagsArr, null);

let mut unclaimed be array(str)()

for (let mut i be 0; i \< len(args); ) {

if (contains(flags, args(i))) {

assert(i + 1 \< len(args));

set(mut flags, args(i), args(i + 1));

mut i to i + 2;

} else {

push(mut unclaimed, args(i));

mut i to i + 1;

}

}

ret (unclaimed, for (let flag in flagsArr) { get(flags, flag) })

}

## Map/Unroll Operator

can also be thought of as the unroll operator

It's kind of like a compile-time version of: print(list(3,4) map {\_+7})

print(\...(((3, 4)\...)+7)) is equivalent to:

print(untup(tup(3, 4) map {\_ + 7})) (assuming map can take a tuple)

print(map({\_ + 7}, 3, 4)) (assuming map can take a lambda then all
sorts of args

print(\...myfunction(whatever)\...) simplifies to
print(myfunction(whatever))

in fact, is it universally true that (\...x...) is just... x? \...i
think so, huh

## Switch/Match

It's basically trying to apply to a lambda even if it doesnt know if it
can.

In fact, can we make if work like this too?

dont need to, cuz can just say myThing match {(Marine(hp, atk)) do stuff
here};

let mystuff:(bool\|int) =

match(myThing,

{(Marine(hp, atk))bool stuff},

{(Firebat(hp, fuel))bool stuff},

{(5)bool true},

{(a: Int)int blorp})

switch {(var: #T, firstCase: ()#R)

firstCase()

}

switch {(var: #T, firstCase: (#F)#R)

\_\_maybeApply(var, firstCase, {null})

}

switch {(var: #T, firstCase: #F, secondCase: #S, restCases: #\...R)

\_\_maybeApply(var, firstCase, {

switch(secondCase, (\...restCases))

})

}

## Implementation

when we stamp a template, we need to put the stamp somewhere

option 1: putting it next to the definition

option 2: putting it (and all functions) into global scope

big +1 to option 2, because in option 1, if we have polymorphic lambdas
inside polymorphic lambdas, we could get an NxM explosion of copies.

when we pass references to categories around, we need to know where it
came from, to put the stamp there. so, we cant just pass these templates
around by value. we need to pass some sort of reference.

the reference could be the name... but a lot of things dont have names.
we'd need to make some sort of generated name for all the anonymous
functions.

or we could have it be an actual pointer to \*something\* that contains
all the options for that value.

-1 to pointer idea. this is an immutable data structure, it will go out
of date and cause inconsistencies.

so we need to go with some sort of generated name. should it be a
number? some sort of string? perhaps it can be like this:
seqnum-originalname or if it's anonymous, seqnum-containingfunction-lam

in fact, we could encode the filename, line number, and char number in
there, and then put a unique number in.

name:filename:linenum:charnum:type

stamps will have the type part at the end.

anonymous functions will just have nothing before the first :

but in the meantime, just do a sequence number.

so, pass 1: hoist all functions into global scope, give them names,
leave these names in their place.

when you pass around these things in the interpreter, theyre just these
references again.

we can use the same thing for closures as we do for currying. impement
closures as just another curry.

a curry is just ... a function pointer and a struct, right?

would it work to have a : between the param list and the return type?

it would conflict with specifying template things {:(T)(a: T) ...}

what if we just disallow those crazy template restrictions for lambdas
(c++ has same limitations) and only have them on outside things?

sum{:(T)(a: T, b: T) a+b} would become

sum:(T){(a: T, b: T) a+b}

it also frees us up to do something crazy like

squared:(N) = #N \* #N; fib:(N) = #if(#N \< 1, 0, fib:(N-2) -
fib:(N-1));

or

pair:(T) {

a: T;

b: T;

}

structs are different from functions cuz functions need the () there.

//extern \_\_add(a:int, b:int)int;

//extern loadCampaign(campaign: ICampaign\$);

//extern rectTerrain(tiles: (string)()Tile, grid: string)ITerrain;

+{(a:int, b:int)int \_\_add(a, b) }

interface IString {

charAt(this: dynamic IString\*, index: int)char;

}

interface IMultistring implements IString {

charAt(this: dynamic IMultistring\*, index: int)char;

strings(this: IMultistring\*)list:string;

}

interface ITile {

walkable(this: &)bool;

}

interface ICampaign { }

struct Wall implements ITile { }

struct Floor implements ITile {

walkable(this: &)bool = true,

}

struct UpStaircase {

floor: Floor

}

UpStaircase implements IFloor

UpStaircase.floor implements IFloor

walkable(this: specialize &UpStaircase)bool = false;

struct UpStaircase extends Floor {

walkable(this: specialize &)bool = false;

}

struct DownStaircase implements IFloor { }

struct First implements IFloor { }

struct Second implements IFloor { }

struct Third implements IFloor { }

struct FirstAmbushSpawn implements IFloor { }

struct MyLevel implements ILevel {

private levelImpl \$LevelImpl implements ILevel,

init(this: &\$MyLevel){

let terrain = RectTerrain(

umap(

\[\"#\", Wall\],

\[\".\", Floor\],

\[\"\<\", UpStaircase\],

\[\"\>\", DownStaircase\],

\[\"1\", First\],

\[\"2\", Second\],

\[\"3\", Third\],

\[\"a\", FirstAmbushSpawn\]),

\"#########\"

\"#..a#..\>#\"

\"#1#.#3###\"

\"#.#.#..##\"

\"#.#2##..#\"

\"#\<#\....##\"

\"#########\");

mut this.levelImpl = LevelImpl(terrain);

}

onPlayerMoved(this: specialize &\$MyLevel){

if {unitAt(player, locOf(terrain, instanceofFirst))} {

addUnit(this, \$Goblin());

print(\"spawned \" + player.location);

}

}

}

struct \$MyCampaign implements ICampaign {

campaignImpl: \$CampaignImpl implements ICampaign,

goblin: &\$UnitClass,

myLevel: &\$MyLevel,

MyCampaign(this: \$&MyCampaign){

mut this.campaignImpl = \$CampaignImpl(

(mut this.goblin = \$UnitClass()),

(mut this.startingLevel = \$MyLevel()));

}

}

loadCampaign(\$Campaign());

=(a:int, b:int) bool { \_\_eqIntInt(a, b) }

print(x: &#T) = print toString x;

print(x: &str) = \_\_printStr x;

print{(first: &#TFirst, second: &#TSecond, rest: &#TRest\...)void

for {tup(first, second, rest...)} print;

}

parseFlags(args: array:string, flagsArr: \...str) {

let mut flags be c.tmap(flagsArr map {\[\_, null\]})

let mut unclaimed be array:str()

for (let mut i be 0; i \< len(args); ) {

if (contains(flags, args(i))) {

assert(i + 1 \< len(args));

set(mut flags, args(i), args(i + 1));

mut i to i + 2;

} else {

push(mut unclaimed, args(i));

mut i to i + 1;

}

}

(unclaimed, flags)

}

LFolder struct template(Result, Range) {

result: Result;

range: Range;

lfold template(Result, Collection) (result: Result, collection:
Collection) {

ret LFolder(result, range(collection))

}

next(this: mut &, newResult: Result) {

LFolder(newResult, next(lfolder.iter))

}

}

main(args: array(string)) bool {

let (inputFilePaths, flags) be parseFlags(args, \"-o\", \"-p\", \"-c\")

let outputFilePath be get(flags, \"-o\")

let parser be get(flags, \"-p\") else \"program\"

let topCode be get(flags, \"-c\") else \"\"

let inputFilesContents be

for (let inputFilePath in inputFilePaths) {

ifs (readFileAsString(inputFilePath)) {

FileNotFoundError {

println(\"Couldn\'t read file \", inputFilePath, \" error \", s);

ret false;

}

c:str { c }

}

};

let code be

for (let (a, b) in lfolder(topCode, inputFilesContents)) { a + \"\\n\" +
b };

// let code be lfold(topCode, inputFilesContents){ \_ + \"\\n\" + \_ };

print(\"Running \", parser, \" parser from \\\"\", code, \"\\\"\");

if (parser = \"program\") {

let sProgram: SProgram be parseProgram(code);

let tProgram: TProgram be skeletonToTemplate(sProgram);

let cProgram: CProgram be templateToCish(tProgram);

let mProgram: MProgram be cishToMetal(cProgram);

let lProgram: str be metalToLLVM(mProgram);

print(lProgram);

} else ifs (parser) {

}

parse(code);

}

Template arguments can be:

-   integers

-   lambdas

-   names of functions

-   names of types

sizeof:()() = 0

sizeof:(First, \...Rest)() = 1 + sizeof:(\...Rest\...);

indexesInner:(Num)() { }

indexesInner:(Num, A)() = Num;

indexesInner:(Num, A, B, \...Rest)() {

ret (Num, indexesInner:(Num + 1, B, (\...Rest\...))

}

indexes:(\...Rest)() { indexesInner:(0, (\...Rest\...))() }

// if something is immutable, then copies are cheap (theyre just sharing
references)

// and there needn\'t be any owning pointers.

// but, it also means that it cant have any destructors or any other
sort

// of event broadcasting, since this might be shared around a lot

struct Null { }

struct \$Player {

id: UUID;

lifeCode: int;

name: str;

points: int;

}

simplifiedNamesEqual{(a: &Player, b: &Player)

unspace(tolower(a.name)) == unspace(tolower(b.name))

}

let a =

Index:(

int,

\[

\[{\_.id}, {\_.id == \_.id}\],

\[{hash(\_.lifeCode)}, {\_.id == \_.id}\],

\[{hash(\_.name)}, simplifiedNamesEqual\]

\],

\[{\_.points - \_.points}\])

struct IndexNodeHashAspect:(index, IndexNode) {

\$nextInChain: \$?&IndexNode,

\$previousInChain: \$?&IndexNode,

}

IndexNodeTreeAspect:(index, IndexNode) {

\$parent: \$?&IndexNode,

\$left: \$?&IndexNode,

\$right: \$?&IndexNode,

}

IndexNode:(V, Hashes:\[\...\[Hasher, Equator\]\],
TreeComparators:\[\...TreeComparator\]) {

value: V;

\$previous: \$?&IndexNode:(V, Hashes, TreeComparators);

\$next: \$?IndexNode:(V, Hashes, TreeComparators);

hashAspects: \[

(\...

\$IndexNodeHashAspect:(

indexes:(\...Hashes\...)\...,

IndexNode:(V, Hashes, TreeComparators)))\];

treeAspects: \[

(\...

\$IndexNodeTreeAspect:(

indexes:(\...TreeComparators\...)\...,

IndexNode:(V, Hashes, TreeComparators)))\];

setPreviousInChain:(hashAspectIndex)(this: &\$, that: &\$) {

mut hashAspects.(hashAspectIndex).previousInChain = that;

}

setNextInChain:(hashAspectIndex)(this: &\$, that: \$&) {

mut hashAspects.(hashAspectIndex).nextInChain = that;

}

IndexNode(value: V, previous:

}

Index:(V, Hashes:\[\...\[Hasher, Equator\]\],
TreeComparators:\[\...TreeComparator\]) {

masterListHead: \$?IndexNode:(V, Hashes, TreeComparators)\$;

hashAspects:

\[\...

HashAspect:(

IndexNode:(V, Hashes, TreeComparators),

(indexes(\...Hashes\...)\...),

(Hasher\...),

(Equator\...)

)

\];

treeAspects:

\[\...

TreeAspect:(

IndexNode:(V, Hahes, TreeComparators),

(indexes(\...TreeComparator\...)\...),

(TreeComparator\...)

)

\];

insert(this: &\$, value: V) {

let node = IndexNode:(V, Hashes, TreeComparators)();

(\...insert(((unseq hashAspects)\...), node));

(\...insert(((unseq treeAspects)\...), node));

}

}

IndexHashAspect:(Node, hashAspectIndex, Hasher, Equator) {

hasher: Hasher;

equator: Equator;

buckets: ArrayList:?Node;

insert(this: &\$, node: \$Node) {

let hash = this.hasher(getValue(node));

let bucketIndex = size(this.buckets);

let owningExistingBucket = swap(\$table.buckets, bucketIndex, Null());

setPreviousInChain:(hashAspectIndex)(\$owningExistingBucket, node);

setNextInChain:(hashAspectIndex)(\$node, mov \$owningExistingBucket);

swap(\$table.buckets, bucketIndex, mov \$node);

}

}

## Uncategorized

We can reconstruct the application the state by looking at the stack of
the interpreted program plus maybe if we need to the variables on the
Heap and so on

Instead of looking at the call stack maybe just have some predefined
Global\'s that have the state or something because if we just do a stack
of kind of like a single with a single stack you can\'t do multiple
things like yeah

We should have the reference count built into every object at address 0
and also we should make it so if in a struct a member has never been
lent to anyone then we can inline it into the struct

Inside every object instead of having a counter of strong and weak
references have a doubly linked list inside every planter that will
point to the next and previous in the entire Loop of the references and
the object that\'s all that they\'re all pointing to should have a
pointer to one of them. this way we can know who is pointing at me.
given a pointer one can figure out who it belongs to by maintaining a
map of memory ranges

If we do that linked list thing with the shared pointers then we can
also kind of build it into the Auto release pool we can make it so the
Auto release pulled delete everything at once meaning that they don\'t
need weak pointers or nullable shared pointers

Nice thing about the link list approach for weak point is is that we can
actively go and no out all of the week pointers and just deallocate the
object right then instead of the C++ way which leaves the thingy intact
for a while. actually, i guess they use the info struct for that huh

Instead of making the size of these pointers bigger by adding the next
in the previous nodes we can do something else we can just have the
pointer point to a separate pointer struct with source, target, next,
prev. each object should also have a pointer to an extended info struct
of its own, containing file, line, shared ptr head, weak ptr head, and
maybe also a list of all contained pointers, so we can do cool crawly
things. if we dont want to change memory layout we can make vptr part of
it and put this in the vptrs place

Have every single variable be just a one length Tuple

Since functions Take tuples as their one argument so to speak and a
single unary thing can be thought of as just a one length Tuple then I
think we get for free the ability to call a one argument function
without parentheses

Problem with having binary infix operators that we wouldn\'t know
whether it was a binary infix operator or just a unary call thing is but
we can we can just in the compiler phase store it as a chain of calls
and then figure out whats binary or unary based on identifiers and so on

Owner pointers can\'t be moved out of but owner or null pointers can be
moved out of this will be helpful when storing stuff in objects as
inline or as pointers

Correction to the last message we can move out of non malleable owner
pointers if it\'s in a return statement

Let\'s have some immutable objects which have the restrictions like they
can\'t have destructors and their Constructors can\'t have side effects
and can only contain other const objects and these ones can have
multiple owners like they can have an owner count and operate like
shared ptrs

have func called newLevel which takes in nothing, thats the impcall
constructor. iow, default constructor is called after impcall.

can we call it imploding?

also rename string and int to Str and Int

Actually we do need to look at the stack because we are going to be
calling a very very blocking the function for when we do the jump slash
animation so we don\'t want to lose the rest of the stack that was
intended to be run

?&?MyStruct

its a option\[&option\[MyStruct\]\]?

or is it a nullable ref to a nullable mystruct ref? not possible,
because you cant have a ref to a ref

can we say that theres only ever one ? in there? that makes sense,
because we only ever have a reference, whether it be
borrowed/owned/shared.

?&MyStruct

can we say that it's only ever the leftmost thing? that means this ref
is nullable.

&?MyStruct

that means it's a reference to a nullable MyStruct, as opposed to a
nullable reference to a mystruct. the latter makes sense.

ok, so ?&MyStruct then. the ? can only ever be the leftmost thing.
