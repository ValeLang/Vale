if we call pure functions, does that copy the data out?

\"interned\" bit? or a region of memory? some way to tell whether
something is interned besides reinterning and comparing?

if we intern objects, we can use it for easy singletons, if they have no
members. will be nice for None.

also, interning objects should probably only be for immutables? how
would we intern a mutable None?

immIfAllImm

can we do analysis to figure out when we can make functions pure? then
we can add arenas and stuff for free.

parser shouldnt do any stateful stuff because it can do backtracking.

maybe make it so we can automatically convert from a constraint ref to a
borrow ref, but we need symbols for anything else

IFunction1\<Int, Int, mut\>

that last one should be optional, that way if people leave it off itll
be fine.

fn map\<T, X\>(list List\<T\>, f IFunction1\<T, X\>) { \... }

even though its left out its assumed mut, which is probably good.

what if they try to hand in an imm func though? thatd be annoying\...
maybe we should make all blocks and lambdas mut by default?

see if we can get rid of the not parser\... its suspicious and might not
work in tmlanguage

why is fillParams a thing? should we fill all of them and not just the
params\', and get rid of AnonymousRuneST altogether?

two approaches:

\- have functions be on the outside, and mark themselves as
\"internal\". but the problem is, we\'d have to figure out what
interface they apply to, every single time. we\'d basically have to
evaluate all rules for every internal method of every interface in
existence, every time we want to figure out what methods are internal to
a certain interface.

\- have functions be on the inside, and just include them whenever
we\'re searching through global scope. kinda hard, since we\'d have to
index them on name, and include their containing structA/interfaceA in
the templata somehow\...

\- or, above option, but just have them be legit first class citizens.
kinda feels like duplication though\... hmm

\- duplicate them. have the functionA in both.

itd be nice if there was a way to combine rune and name, so that we
could use the same astrouts for the internal and external one.

thats probably the way to go. have it in both, have them use the same
astrouts. though, the one outside will have more rules attached to it?

and if its nested in a nested struct, dont we need all the rules of
every level?

this is a broader question of how we do UFCS.

what if we include the containing struct/interface in the
struct/interface/functiontemplata? or, enventry or whatever

that could work.

yes, each struct/interface/functiontemplata will contain a list of
parent structa/interfacea that it came from.

we can probably really easily detect if something should be inlined in a
struct\...

by putting \@myRegion before a function call, thatll make it use that
allocator for all its allocations.

perhaps it can do that with malloc/free hooks?

it makes it so we dont need to make the region another template
parameter.

also maybe we can do the readonly/readwrite stuff with page protecting?
is that slow?

stop using Templar in the name, its as bad as Manager

[[https://www.reddit.com/r/programming/comments/44qa2b/modern_c\_memory_management_with_unique_ptr_stop/]{.underline}](https://www.reddit.com/r/programming/comments/44qa2b/modern_c_memory_management_with_unique_ptr_stop/)

a lot of people expressing that you rarely need shared ownership.

dont we already specify the types in the array construct temporarily?
maybe we dont need to do the circuitous stuff yet.

Every parameter in the pack will need a rune. Also, every pack itself
will need a rune.

Can do a depth first search from the topmost pack. If it\'s a struct,
search through all possible constructors of the struct. If it\'s a
sealed interface, search through all possible constructors of all
possible structs.

\...in the current environment.

but how do we connect the arguments to the param types? we need to, so
we can do this long distance connecting. perhaps we make it so param 0
is rule 0 and so on.

we should totally make an interactive template call debugger, some sort
of thing that lets us step through. can write a lot somewhere. would
help a lot.

for now, first step should be just handling lambdas coming in into an
interface. but gotta somehow get the Int into the lam, to stamp\...
hmm\...

narrowing it all down by num params would speed it up probably

what if a pack is something with missing information, it\'s a bunch of
rules supplied by the caller?

a HasConstructor rule, HasConstructor(#S, #Args\...)

param rule: #1 = Call(IFunction, List(Int), #T)

param rune: #1

arg rule: #1 = HasConstructor(#S, List(lambda))

itll have to be a smart rule that puts allllll that together.

might need to propagate down that #S has an ancestor template IFunction,
whose first param is an int\...

this is probably similar to how we search through all superclasses.
except now we\'re searching thru subclasses, and searching by
constructor. the callsite is adding rules to search for ? super T
basically, so shortcalling can add rules to search for ? extends T.

its probably going to be simplest to do a dfs. super slow but itll do.

problem: shortcalling is usually only for sealed interfaces. we\'re
feeding in a closure to an open interface.

once we have borrow refs, then it would make sense to have block lambdas
only give borrow refs.

maybe a lambda should be kind of like a pack, becoming whatever type
it\'s assigned into?

but that would make special template goodness not work. it really should
stay a struct.

we have to figure out something here\...

Array(5, ArrayGenerator((i){ i }))

Array(5, ({ 0 }))

the thing is, we will \_always\_ want that lambda to conform to
something, whether it be a concept or an interface.

what if a concept could be a type? i dont see why it couldnt be\...

concept ArrayElementGenerator {

  fn call\<T\>(Int) T;

}

fn Array\<G\>(n Int, generator G ArrayElementGenerator) {

}

now, that lambda has something to conform to.

since we always want the lambda to conform to something, we can decree
that the shortcalling parens are implied.

Array(5, { 0 })

template\<typename T\>

concept TupleMapping = requires(T a) {

    { a(0) } -\> true;

};

concept TupleMapper {

  fn call\<T\>(T) X where(X: Ref);

}

fn map\<E, M\>(tuple \[E\...\], mapper M TupleMapper) {

}

or we could have a keyword \"tfn\" which says \"hey leave this as a
functor\" lol

\...thats probably the simplest\...

or we can allow it to remain a functor, but whenever we pass it to a
function we have to conform it to a concept. that sounds reasonable.

another reason we should have it be implicitly converting is that it
cant use shortcalling syntax\... we cant match our \_\_call to whatever
the receiving interface\'s method is.

what if\... trivial destructors can be automatic, but anything more
complex needs an explicit destructor? that way we wont have magical
behavior

though, it means interface destructors are always explicit. annoying.

can we use an operator as a hint to do covariance? Honda can +extend
Car, and thatll make sure the Car methods come first.

if we wanted an array generator/consumer that could fail and return an
error type,

Also, if you\'re curious about the ultimate goal: WebAssembly is someday
soon getting \[Reference
Types\](https://github.com/WebAssembly/reference-types/blob/master/proposals/reference-types/Overview.md).
This means that WebAssembly will someday be able to have references to
the host environment (such as reference to JS\'s HTML DOM). AFAICT,
these are conceptually identical to C++CLI\'s
\[handles\](https://docs.microsoft.com/en-us/cpp/extensions/handle-to-object-operator-hat-cpp-component-extensions?view=vs-2019).
This means that if we can make dotnet-webassembly correctly do
WASM-\>CLR (and eventually reference types to handles), we can use any
WASM-targeting language (like Rust or Cone or my own experimental
language) to write fast, modern, GC-less code with easier interop than
ever before. It would be a huge game changer for everyone involved =)

maybe compile wavm in c++cli?

we could just make our weak ptr an i64 index into the global map, if we
want to cut down on size. we\'d suffer the cache miss tho. worth
considering.

like chandler carruth said in his 2019 cppcon talk \[link\], there are
no zero cost abstractions. rust\'s borrow checker makes you restructure
your program in a sometimes harmful way. often, weak pointers are
represented as generational indices into a centralized map, which means
that you need access to the map if you want to dereference it, and then
the runtime cost of that cache miss on the map itself. it\'s not free.
the question just becomes, where and how do you want to pay your costs.
-v

\"fast, safe, interoperable language thats easy to learn\"

how do we interoperate w c++? can we use wrapper objects? can the fat
pointers basically \*be\* those wrapper objects?

could we have a unique_ptr point at an object containing the fat ptr?

we make none with () but destructure it with \[\] thats kind of
unfortunate\...

we could use a symbol to mark the input to a func instead of \_, thatd
disambiguate\... \~ perhaps?

\_ would only be in patterns, not calls.

mat m {

  \_ Marine(foo(moo(\~, 4), 8), \_)

can disallow nested calls, must chain:

  \_ Marine(moo(\~, 4) foo(\~, 8) 3, \_)

or, disallow chaining:

  \_ Marine(moo(\~, 4) == 3)

anything more complex can go in ifs

\[\] probably better. more honest.

perhaps we can use a symbol to denote a call?

= \~ \`

perhaps, if theres at least 2 things after it, its a call?

  \_ Marine(foo(\_) true)

  \_ Marine(foo(\_) \> 9)

  \_ Marine(\_ \< 9)

if theres 2 things and no parens, its a type check

  \_ Marine(\_ IUnit)

 

\- if theres one thing, no parens, its a capture

\- if theres one thing, w/ parens, its a destructure

\- if its two things, no parens, its a capture and a type

\- if its two things, parens on first, its a\...

  - call whose return is fed into a variable

\- if its two things, parens on second, its capture and type and
destructure

\- if its two things, parens on both, its a call whose return is fed
into a destructure

\- if its more than 2 things, its an expr with binaries.

so basic rules:

\- if a call is followed by another call, the first one is a function
and the second one is a type destructure

wait, how do we check if somethings an interface? we cant destructure
those\...

perhaps post a blog post talking about extern refs, and ask people, how
else can vale excel at cross platform coding?

rust can have things pointing in from the outside, it would require
using weak pointers, and that would mean using Rc and probably refcell
and they dont like doing that.

apparently kotlin native can\'t take in arbitrary jars?

\"single ownership doesnt have to be this difficult\"

peverify

ilasm
[[https://docs.microsoft.com/en-us/dotnet/framework/tools/ilasm-exe-il-assembler]{.underline}](https://docs.microsoft.com/en-us/dotnet/framework/tools/ilasm-exe-il-assembler)

use \[\] for destructuring

use () for calling

if we see three things in a row separated by spaces, there\'s binary
operators involved and treat the entire thing as an expression, no types

if theres just two things in a row separated by space, then the second
one\'s a type.

if at least 3 in a row, must be an expression

if 2 in a row, second must be a type

dont allow checking type and feeding into an expression, must use
if-statements

mat unit {

  \_ Marine = 7;

  \_ Firebat = 10;

  \_ Ghost = 11;

  \_ = 13;

}

its nice having common code so that we can have a command line
interface\... also makes it better to integration test.

maybe we dont need colons?

name Str = 4;

name! Str = 4;

(a!, b Marine) = foo();

name! &!Marine = \...

do need them in pattern matching probably\...

mat unit {

  \_ Marine(hp) = 4;

  \_ Firebat(fuel) = 7;

}

huh. worth experimenting with.

maybe cant do binary in patterns\...

mat unit {

  sq(\_) eq y

support unary call \<(4, 5)

i want vale to be known as fast, interoperable, and easy.

how do we deal with

a \<= b

do we interpret

\< must have nothing to the left of it, and \> must have nothing to the
right?

how do we know that this

\<T\>(a: T, b: T){ \... }

is doing template stuff rather than being an identifier called \<T\> ?

and worse, there might literally be an \<=\> op\... though thats
probably fine because = cant be a func name.

foo(moo\<T, X\>(a)) might be misinterpreted as:

foo( \`moo\<T\`, \`X\>\`(a) )

i think\... we should disallow \< and \> in function names except a
whitelist: \< \> \<= \>= \<=\>. same with = and !, allow == and !=

apparently segmented stacks are needed to make coroutines fast, but they
make ffi more expensive because they have to switch to a bigger stack.

though, go now uses \"stack copying\" which works like a vector. but now
we have to update pointers to things on the stack, which kind of
sucks\... it would only work for us if we have no inl things on the
stack, which we absolutely need. nope, our stack must remain stable.
which means probably relying on overcommitting memory or segmented
stacks.

but segmented stacks needs a check at every function entry\... bollocks.
maybe we should async await?

we\'re making managed DLLs so it should be possible to call into the
outside world using managed refs\... right?

for now, lets just use integers to represent pointers and have a hash
map on either side. on the C# side there will be a hash map of ID to C#
object, and a hash map of ID to i64-disguised pointer. the C# side will
be weak refs, and allow borrowing via a closure call.

remember, everything is eventually an integer\... everything.

we might have to specifically export certain types and functions.
perhaps we shouldnt allow a huge spiderwebbing transitive automatic
exporting\... maybe by whitelisting only certain functions we can avoid
it. perhaps we can also mark certain things as opaque.

but these rapid repeated calls into c++cli might be slow\... for that we
can allow moving structs and functions into the c# side. maybe. also, if
using IL2CPP there should be no overhead.

pure functions are PERFECT for arena allocating! things have to
transition the boundary to come out anyway, thats the way the user can
be explicit about surviving objects

if we have a locked region, we can safecall into it at the same time as
locking it for writing. watch out though because we could be leaving it
in a permanently broken state.

since we can switch all weaks to unowned (which either use backrefs or
keep-alive ref count) we can do that in ill-behaved regions\... but only
if theyre temporary and isolated can we blast them away. basically, we
have to do a safecall.

how do we make it so we can pass in a lambda into a receiving interface
kind, without the shortcalling parens?

we can automatically shrink vecs when we\'re low on memory\... but only
if the elements arent inline. luckily thats gonna be rare.

instead of unlet, could just call drop directly.

\...except that doesnt work for borrow refs. need a way to clear those
out.

actually, borrow refs can be automatically unset on their last use!
though, that might be a bit confusing to some who might want them to
last longer\... must weigh those two options.

(a, b)Int{ \... } is unambiguous because binary operators need spaces
around them!

wasm needs a malloc implementation anyway, so lets make one! or rather,
fork jemalloc or something else to optimize for vales use case.

can we (at runtime perhaps) mark an object or a class as leaking?

it would set a certain bit on it, which marks the object as \"shared\".
it would unfortunately mean that borrow refs have to check when that
thing gets to zero, so they can free it. how bothersome. we can put a
branch prediction in there so it doesnt slow us down.

how much extra space is used by rust\'s approach?

everything is in an array, and if something uses a generational
index\... then that\'s an extra integer per reference, and like an extra
integer in the object itself.

in vale, there\'s an extra integer in the object. if there are any
weaks, then there\'s an entry in the weak table.

uni-ish reference in rust wont have anything referring to it. we could
have uni references in vale too though, and that would disallow any
constraint refs or weak refs, only allow borrow refs.

so, we dont have to worry about this extra space much.

where clause on each param

we can use \< and \> as regular functions by putting them in parens,
like (\<) and (\>)

if we scan all mutables that cross thread boundaries, then we can
actively know what mutables belong to what threads\... instead of paying
that cost on malloc and free we can just pay it on transfer between
threads. somehow.

List\<Int\> isnt working\... List\< is, but then we consume \"Int\>\"
and then theres no \> to complete the call so the entire thing fails.

perhaps only have two places where we can have special characters in
names:

fn \"\<\"\<T\>(a: T, b: T) { \... }

5 \< 7

so, as the binary operator separator between expressions, and the quoted
name of a function\...

how do we dynamically link vale things? perhaps once compiled we cant do
weak or constraint across the boundary? one has to depend on a VIL if
they want to have weaks and constraints w a library.

we could use malloc hook to have borrow refs to arbitrary c/c++ objects.
could have a borrow or weak ref to it, and we would run the right logic
on free. we\'d need a global map for sure\...

could constraint ref behavior be specified on a per region basis? that
way we can put a dependee module in a region and specify that its
constraint refs become unowned refs

talk about how the interop problem probly leads to things like xam and
kot nat where the language takes over the entire app.

an unsafe ref keyword which marks a certain borrow ref as not
incrementing/decrementing? or is that mostly obviated by borrow refs

in single ownership article, open by saying like \"i hope those who use
rust will be surprised by how easy and flexible this can be\" and \"i
hope c++ users will be surprised by how safe and clean this can be\"
then proceed to explain.

thatll get their attention.

maybe also mention that one can implement these in c++ and rust on
shared_ptr and rc. can maybe even introduce classes that can
conditionally compile them out. make sure to mention how we allow
pointing to a changeable obj, and rust will need refcell to do it\...
but theyll be subject to refcell restrictions like the runtime rwlock.

mention how this is zero cost on wasm, since wasm sandboxes away access
to outside world.

ask some swift devs if we can get the strong ref count somehow

cant share js because no finalize

My team owns an Android (Kotlin) and iOS (Swift) app. The app is
responsible for processing data, submitting to ML models, and then
displaying results in a UI. We chose to do a lot of that processing in a
shared Rust component. What we have found is a high overhead of bridging
Kotlin/Swift to Rust. Some core logic has also seeped into our bridging
code resulting in some duplication (exactly what we were trying to
avoid).

c# will be a competitor too, not just rust

c# kind of misses the point though, because its garbage collected. its
interop story cant be nearly as clean either can it?

doesnt it need a huge runtime too

\"I don\'t mean to piss on anyone\'s parade, but cross platform UI is
probably the hardest problem in software engineering. There have been a
dozen or so teams of people smarter than us who have tried and failed.\"

benefit of cross compiling might be using other languages existing
libraries?

if i want to use a c# library in the browser, can i? maybe id want to
have vale compile to a .dll so it can do that?

maybe compile vale to llvm to wasm to cil dll, thrn use that in a c#
project, then merge all those dll files, and use blazor to compile to
wasm! loool

wasm is sandboxed so we can disable constraint refs most the time

now that we be doing weak indices, we can have a mode when compiling to
turn all borrow refs into weak ones, if theres any crashes.

alternately we can turn all borrow refs into share refs, and keep the
target object alive. when we deref a borrow ref, we do a
branch-predictable assert on an \"alive\" bit in the borrow count. in
other words, the sharing keeps it alive, and that bit makes sure we
never use it.

moo() in IX is diff from moo() in IY. thats the diff tween jvm style and
fatptrs

so, we can do interfacing a little better than rust\.... but can we find
a reason to cross compile onto the managed language?

perhaps so that we can run certain functions of objects from both inside
and outside? imagine if we had a starcraft game, we would want the host
env to be able to call the same readonly functions\... ah, but they can
anyway, just call into the native side from a wrapper.

maybe the strength of v can be automatically generating those wrappers?
rust would probably have a lot of trouble because the outside cant have
a borrow ref.

maybe compile to jvm for debugging?

maybe a polyfill for no-wasm envs?

how can observers possibly function w indices

\"when i click on this button, delete this base\"

is this inherently needed in cross platform code?

yes. attaching to button listeners, if the core manages views. attaching
to network managers definitely, like in a chat room.

if view is calling into core, must supply mutable world state. whether
via observer or not.

solutions:

\- route messages from the base layer upward

\- use threads and channels?

in rust, if you have an interface, the underlying concrete type only has
an ID or something to refer to a Blark. another concrete type might have
an ID for a Florg. another concrete type might have an ID for a Shrog.
lets say we have an interface method that says to \"do the thing\". that
interface method has to take in references to all the blarks, florgs,
and shrogs.

can seamlessly interop both ways, no hiccups. can talk both ways. extra
benefit would be doing things natively when possible?

if we have an extern region and its c++, can we take constraint and weak
refs to it? i think not, we can only borrow\... crap.

if only we could hook into malloc or something to know when the extern
thing goes away\...

well, the semantics of extern can vary according to the outside, that\'s
totally fine, happens with gc. we\'ll just say, hey, it\'s unsafe to use
constraint and weak refs on an extern region, so be careful.

vale could be the fastest way to do unity games\... hmmm

extern should br a region. if gc\'d, owning \_and\_ borrows should both
mean gc. owning means the core is responsible for calling something on
it. borrow means its not. if java person says \"you dont have to call
anything on it before it goes away\" then core should receive a borrow.
if core is responsible for calling something on it before it can go
away, it should be an own.

also, remember, even in gc\'d languages, we often need a concept of
ownership, hence dispose.

can we have weak references to things outside? i dont think so\... maybe
lets outlaw them? or simplify them to be always present, like we do with
shared?

wait, does that mean single ownership is compatible with GC?

\...i suppose it is, but\... constraint refs and borrow refs still dont
make any sense in the context of gc.

does that mean we can have gc regions?

if we use wasm, the nice thing about writing the outside in v is that it
supports weak references into the native region. it can do that super
fast because its nonatomic nature. it can seamlessly move things between
the two because of the clusters, which are bounded because single
ownership?

ah, it can have deterministic weak refs into the native region because
weak refs require single (or arc) ownership.

so, having pointers inside requires single or arc, and single is better.
rust cant do this cuz it needs a global table of ids.

btw this means we have to permanently reserve ids. gross. perhaps we
mark them as used then? or perhaps use the finalizer of the outside to
know when theyre freed! thatd work.

so, this is better than rust because we have better access to \_inside\_
the native area.

also, for v code that\'s compiled to platform, we can inc/decr the
borrow count. thats the advantage of sharing platform code.

downsides of rust w.r.t sharing code:

\- it\'s rust; learning curve

\- can only refer to things inside by ID (like the rest of rust)

maybe the region can specify if it only allows borrow refs or if we can
have borrows. that way extern can be a region. or perhaps it doesn\'t
need to be; it could be the containing type that only hands out borrow
refs? we would just make sure that we cant get any non-borrow ref from a
borrow ref.

having extern as a region would be nice because we could do seamless
transfer between the outside and inside.

game.valence

interface GamePresenter {

  fn attack(attackerId: Int, victimId: Int);

}

equivalent game.vale:

extern native interface NativeGamePresenter {

  fn attack(attackerId: Int, victimId: Int);

}

export struct GamePresenter {

  inner: NativeGamePresenter;

  using inner.\*;

}

itll generate an interface for the core, and some classes for the
platforms.

can write your core code in c or c++. if a rustacean wants to step up,
they can make rust back it too\... swift as well. but we\'ll stick with
c++.

we\'ll call it project corvala

can we hold onto pointers into other regions? no, because then we\'d run
into race conditions.

which means \"extern\" should probably not be a region.

if a rustacean is against crashing, remind them of all the .unwrap and
panics

blog post named Experimenting with Constraint References

difference between my and verona\'s regions is that i can guarantee no
references to outside world. when something passes the boundary, i know
its hierarchy. they probably dont, and so they have references between
regions.

this also means i can change the representation, for example normalized
in chronobase, or columnized in firebase, or into extern objects\... or
into native ones!

we can have borrows and weaks be in the obj header. the 64b will be
divided into a high 32b weak index and a low 32b borrow count.

the weak table will have just a pointer to an array. the first entry
will just be a pointer to the obj. the rest will each be pointers to
weak refs.

each weak ref will be an index into the weak table and an index in the
vector. if these are zero, the weak ref is null. the weak ref can also
have a ptr next to it, to avoid the triple deref.

when the obj goes away, first it asserts borrow count is zero. then, it
goes through each entry in the weak array, and clearing out the indices
of all the pointees to zero, thus making them all null. bam, done.

its particularly awesome because theres no hashing or anything.

if borrow checking enabled, the top 32b could be an index in the global
weak table. the weak table has a ptr to the obj, so if it moves (from
rehashing) can update the index in the table.

[[https://www.slideshare.net/KTNUK/digital-security-by-design-security-and-legacy-at-microsoft-matthew-parkinson-microsoft]{.underline}](https://www.slideshare.net/KTNUK/digital-security-by-design-security-and-legacy-at-microsoft-matthew-parkinson-microsoft)
also believes in regions

airbnb had troubles w react native interop
[[https://medium.com/airbnb-engineering/sunsetting-react-native-1868ba28e30a]{.underline}](https://medium.com/airbnb-engineering/sunsetting-react-native-1868ba28e30a)

we should syntax highlight variable declarations, instead of having let

can have our own free lists on top of malloc. that means we dont have to
do expensive malloc stuff\... benchmark it to see if its worth it. its
nice because we can still give it around to free it.

can use template functions to implement compile time reflection super
easily. have a typeinfo cfn like typeinfo\<int\>, dont even need parens
afterward. then to access its stuff use . or :

typeinfo\<int\>:numMembers

with v you can learn its complexity gradually rather than up front (wrt
lifetimes and regions)

exported generic functions can automatically do the
generics-to-go-itable transform

can vale make use of wasm\'s anyref better than rust?

vale can just make it an extern ref. if we have an extern function that
takes an extern ref, thats a regular JS func. come to think of it, we
probably cant give non-extern

lets make inferring a keyword on functions.

not needed on lambdas.

i really want some sort of monomorphization\... thatll probably come in
the form of go-like interface/typeclasses.

maybe we can introduce a \"clone\" operator (convention, not built
in)\... maybe © lol

how to do iterators?

iter.advance()

or mut iter = next(iter)?

or mut iter = iter + 1;

do we want generics or templates?

also, we could do template specialization groups, instead of scattering
it everywhere like c++ does\...

do we really want to compile to JS? itd mean using bigints, which would
be annoying\...

instead, say that regions cannot point into other regions. regions
neednt be bounded, all of native is basically one region. but\... dont
we have native refs? maybe native isnt a region\... hmmm\...

(make this into a doc. figure out the region thing)

how could we make some sort of interface between platforms, separate
from the language? some sort of agent definition thing\...

struct MyRequest imm {

  hp: Int;

  mp: Int;

}

struct MyNotification { blah: Str; }

interface MyController {

  fn addAThing(&this, request: MyRequest);

  event onSomething(moo: MyNotification);

}

is there any other way to do things other than events?

the underlying vale code would be:

struct MyControllerImpl {

  impl for MyController {

    native fn addAThing(&this, request: MyRequest) =

        extern(\"MyController_addAThing\");

  }

}

and that can be pretty easily autogenerated.

actually \"native\" shouldnt be on the struct, it should be the region.
we need some go-between function to do the transition across regions
though\... hmmm\...

would it have to be a C function?

can we not generate a c++ class?

can we generate a c to c++ forwarder?

maybe we could instead say:

native extern struct MyControllerImpl {

  impl for MyController;

}

which means its really defined elsewhere and this is just a mirror of it
really.

extern structs with impls become c++ classes.

itd be really nice if we could do this with only one copy between
regions. maybe we can, if we use C as the default native thing, and it
just calls into vale or c++ (or rust, if someone wants it).

i still think we should go with the global table for zero-costness. it
can incrementally expand. we can also reuse that table for guids for
interop.

the table can incrementally expand, by having a integer on the old
table, and we consistently move two slots forward every time we insert
something in the new table, and if somethings there, we move it to the
new table. while theres a resize in progress we do two modulus in
parallel so we can find the slot its in.

we can also do some moves on lookup and remove so we dont get stuck in a
two-table purgatory.

also, since all this only happens when we actually use weaks, it doesnt
interfere with the optimizer too terribly.

probably want a global function that checks whether the \"expanding\"
bit is set, so the cpu predictor can get that right most the time.

also, since SIMD makes it so theres no difference between one mod and
eight mods, why not do eight moves at a time? or rather, 8 slots at a
time. do all those moduluses, and then put them into the map. though\...
the looping might be a bit much. maybe 2 is the best. profile it!

every chunk of empties will start with an empty entry that has the index
of the end entry, and end entry with the index of the first. when we
remove we can update these. but\... not sure what to do on insert.

perhaps we can use a bitmask to see whose present in the map still?

if we knew the bucket size of the object, we could divide by that to
chop off the trailing zeroes in its address, which would make for a much
faster hash.

perhaps we shouldnt have a massive hash map for our weakptr lists,
because the resize could lead to some pretty gnarly spike in latency.
but then that just leaves\... increasing the object size. bollocks.

we could have two maps; one for the next size class, one for the current
size class. could parallelize the lookup in the map. we\'d slowly
transition things from one to the next somehow. itd be gradual resizing.
dunno how we\'d pick up the stragglers though.

perhaps we\'ll offer a mode which lets us put the pointer in the object
itself. with no weaks itll just be an integer right there in the obj.
with weaks, itll be a pointer to both an integer and a list. can
distinguish by the low bit; borrow incrs and decrs go by 2. we can have
a different function for each type which will deal with that list, that
way the branch predictor can get insanely accurate.

and now, the optimizer can reason a little better about it.

too bad we\'re using 8b per object though. perhaps we can also make use
of it for other things, such as unique IDs or something?

or, it can be 4b for a borrow count, and 4b index into a giant weak
table in the sky. probably a lot harder to optimize tho.

or we can separate the weak tables somehow\... by type or region?

or use a btree?

all our immutables should have withXYZ functions, like withHp, withMp,
etc, which can return an updated copy. we should make the compiler or
runtime smart enough to know when it can reuse the old object.

on jvm, can still have a counter of \"live\" objects, incremented in
constructor and decremented in destructor. when the program ends we can
check if its zero and if not warn of uncalled destructors.

all functions at the top level will have to have fully concept\'d types.
or, they can opt into super flexible wild west template functions with a
special keyword.

lambdas however need no special keyword, theyre wildwest by default.

the generic functions will have an extra param at the end which is an
interface that can do all the things. for jvm itll work with just
objects. for swift itll work like swift objs do, with objc_msgsend. for
native\... we\'ll probably have it pass in an array of func ptrs, or if
c++, a class, and use void\*s.

this extra param will likely take a \_ton\_ of functions\... well,
really only the ones listed in the concept.

maybe we can have a binary size option in the compiler that can do this
to as many functions as possible\... with perhaps whitelisting of
certain functions which are monomorphized?

interfaces methods neednt have a this param. i might want to pass a
group of function pointers that have nothing to do with an obj.

in a way, a fat pointer is basically auto-currying\... on that note, why
not throw two obj ptrs in, or three, or a dozen?

Eq could have a default implementation which uses compile time
reflection to figure out whether things are equal, and it calls Eq on
each of those things. boom. done.

in fact, this might be a good argument for virtual templates\...

same with serialization, printing, toVon, recursive searching for
something, any kind of traversal\...

interface Eq {

  fn ==(virt &this, that: &Eq) Bool {

    \_: &T = this;

    if (not(that is T)) {

      ret false;

    }

    let (thisFields\...) = this;

    let (thatFields\...) = that;

    {\...

      if (not (thisFields\... == thatFields\...)) {

        ret false;

      }

    }

    ret true;

  }

}

talk about how there\'s momentum leading to vale.

\- C++ has showed us the power of unique_ptr, and a lot of people are
waking up to its usefulness, and finding that they have a lot less
memory problems with it.

\- rust is showing us that lifetimes can be incredibly useful, even on
top of other things like how you can borrow an RC. Cone is also showing
us this.

\- Cross-platform architectures are really proving themselves. (not
really true)

\- WASM is coming, and we\'ll soon have a very portable binary, and
we\'ll need a way to seamlessly call into it from whatever runtime in a
seamless way. (not really happening yet)

\- LLVM is enabling new languages, and LSP is enabling tools.

drive home the point that these things are all converging to bring us
vale. speak of it like an inevitability.

\...maybe this isnt really that convincing. maybe we should come at this
from a different angle than inevitability.

we could do some sort of JIT thing like c#, to uncompress template
functions.

or, we can do something with concepts to somehow reuse stamped
functions.

perhaps when compiling to JVM and C#, we can reuse templates like this?
after all, everything\'s just an Object under the hood, why not?

we solve all the problems present in
[[https://conscientiousprogrammer.com/blog/2014/12/21/how-to-think-about-rust-ownership-versus-c-plus-plus-unique-ptr/]{.underline}](https://conscientiousprogrammer.com/blog/2014/12/21/how-to-think-about-rust-ownership-versus-c-plus-plus-unique-ptr/)

use ! instead of var.

a! = 5;

for (a! = 0; a \< 6; mut a++) { \... }

watch out, we can easily move the space and have it be a != 0 which is
something else. i dont think itll be a problem long term though.

also, a != 0 will probably be caught by mustuse!

rust v c++

[[https://news.ycombinator.com/item?id=21469295]{.underline}](https://news.ycombinator.com/item?id=21469295)

if an object owns itself via field m, and we set m to null, what
happens? first, it does a indiana swap on m. then it deletes the
temporary. we good.

in c++, it might destroy the instance then seg fault on the set.

what is a chronobase? is it a module? a region?

its probably closer to a region. but its weird because it has Root. then
again, doesnt every region have a container that can be used to access
it?

so whats the chronobase compiler? sounds like its not making modules,
its making regions? but the imm things arent in a region.

i think we\'re making chronobasecs make types and also regions, though
so far only the chronobase region is supported.

one diff\... cant c

we want to make a superstructure that uses immutables imported from the
outside\... so we can store interesting domain-specific stuff in our
database. but perhaps we shouldnt share like that? is it a bad idea, and
should we have a translation layer inbetween? after all, superstructures
are a DDL, not a general purpose language, and we want to do special
things like proto-ish syntax\...

NO! stick to your guns! such a world is possible! we can do this!

we need superstructure regions to be able to contain other regions, and
use immutables from anywhere. but that means it has to be a hammer-level
transformation\... which we kind of already expected.

but ccs is a code generator\...

modules will be distributed as scoutputs\... that way we can stamp
templates from across package boundaries.

during the templar phase we need to be able to talk to another module,
use its types, stamp its templates, etc\... and output just a module,
not its dependencies, as a metal module. and a checksum.

later on, when we want to make the final exe or dll or whatever, we take
all the metal modules. if there\'s any duplicates we\'ll realize it
then.

right now, chronobasecs is serving as a preprocessy vale generator.
would we ever want it to use external stuff? if so, how?

java and C# both have try-with-resources, so perhaps we could
temporarily lock an object, with finally blocks. it\'s just within a
stack frame, but better than nothing.

can we combine lifetimes and regions?

can we include the region info in the lifetime?

both &\'r and &&\'a use comma. lets back up, zoom out, is there any
better way to specify something there?

we can use numbers, like &1

we can know that because theres an ident, a space, and something else,
that its a region\... &r Marine

we can declare r as a region up front:

fn moo\<r: Reg\>(x: r &Marine)

declaring up front can work for lifetimes too:

fn moo\<a: Lifetime\>(x: a &Marine)

combined:

fn moo\<a: Lifetime, r: Region\>(x: a r &Marine)

we can use \` for region:

fn moo(x: \`r &Marine)

we probably should use a template arg for region, since they can be so
varied\...

fn moo\<R\>(x: R &Marine)

can we put lifetime annotations left of the colon?

fn moo(x \'a : &Marine)

its not a type as much as its a contract\...

can use \" for region!

fn moo(x: \"r &\'a Marine)

NICE

rust & is our \'&

rust &\'a is our \'a&

conclusion:

& is constraint ref

\'& is borrow ref

\'a& is borrow ref with lifetime a

\"r& is constraint ref with region r

\"r\'& is a borrow ref in region r

\"r\'a& is a borrow ref region r lifetime a

the native/nonnative boundary \*could\* be a region\...

\...but itd be nice to have a pointer from vm side to an obj on native
side. to move between we would need a cluster.

maybe we should have some way to have a pointer into a region? basically
a decorated integer, functioning as a weak pointer? probably useful in
general for regions.

jvm is good for very imm heavy programs, cuz of the weak ptr in every
mut. luckily the borrow count is never in prod builds in jvm. so no
matter the plat, only 1 word extra per imm.

&con could be a constraint reference

qq: doesnt every borrow need to be on top of a constraint ref?
otherwise, cant someone use a different reference to blast away the
thing we\'re borrowing? if so, we \_need\_ to make constraint refs one
symbol.

we can make it so && on an owning will make a borrow first.

we can still do \* and &\...

is && a different kind of reference, or is it a constraint ref with
certain promises and extra information?

a && can receive a &, its just a borrow ref, but the optimizer can go
crazy with that extra information.

if we do a = &b, then we actually have the lifetime info right there. a
is a &&\'b B. so really, we only need annotations on function headers
and struct headers. score!

we can automatically infer the lifetime of a container is a subset of
all the members, but if we want we can also do things manually:

struct Moo\<\'b\> \'a where (\'a \< \'b) {

  // nothing

}

can have a static bool that says i am in the main thread. since all
singlethreaded its good.

continuousfuture, gives you the first value when it comes in and then
keeps updating you. at that point maybe its less of a future and more of
a stream\...

just have addmidslistener, and then cardpresenter can react by adding a
bunch of cardlisteners. cardstack need only take lambdas!

completers are awesome! dont produce futures, just take in lambdas

oh wait, we need to call async tho. dang.

sucks that we need futures when we know we outlive the damn thing\... oh
actually\... members outlive containers. so containers having futures
makes perfect sense. members dont want to have futures gotten from
parents though maybe dunno meh

can we still just only give out futures? cardpresenter would listen via
a bunch of futures for the stack events? itd work, its arguably the most
consistent. its just a lil annoying to have to keep those futures in
cardpresenter as members. any way to make that more bearable? perhaps
have a struct containing all the card-specific crap?

maybe we need a cardstackcontroller that takes or owns a cardstack and
also stores futures and stuff like the SetHiddenFid futures.

i think in the end, being forced to hold onto futures is a good thing.
it guarantees safety. we just have to bite the bullet and accept that
it\'s a thing.

let\'s simd modulus whenever we can\... if theres ever a time that we
make weak refs or shared refs to multiple things in a row, we can
parallelize the hash modding.

\...or we can use \>\>3 and & size\... probably way faster\...

a borrow ref into a readonly region cant panic. full stop. the more you
can deal in readonly regions, the better.

regions can contain regions too\... can an outer region point to an
inner region when its readonly? after all, main can point to a locked
region\... but then imagine we close the outer region\... what would
happen? i think its impossible right?

when you run into a constraint ref, you have four options:

\- fix it, so the borrow ref doesn\'t outlive the object.

\- make it a weak pointer.

\- restructure your object hierarchy to follow the DAG pattern.

\- rearchitect your program to use regions so the compiler can help you
out.

a pure function is one where all parameters come through as &,
non-mutable. but then, if inside the function, we make a new object in
our new region, and then pass that plus one of the original params to a
function, \*it\* has to think in terms of two regions. i dont think pure
functions are a surefire way to not think about regions.

fn moo(a: &\'r A, b: &\'r A) &\'r A {

  x = List(a, b); // a List\<&\'r A\> but the list itself is in the
default region

  y = A();

  blah(a, &y);

}

fn blah(a: &\'r A, y: A) {

}

so really, we\'re annotating things from other regions. we always have a
\"this\" region. worth playing with, but im wary of specialness like
that. let\'s see how it plays out.

fn moo(a: &&Marine, b: &&Firebat) &&IUnit

if we follow rust\'s rules for now, we cant do this, must specify
lifetimes.

fn moo(a: &&\'x Marine, b: &&\'x Firebat) &&\'x IUnit

can we turn a constraint into a borrow implicitly?

z = moo(&a, &b); \<\-\-\-\-- lets go w this

maybe constraint refs should be &&\... if we want to encourage use of
borrows.

no, want them as equals?

\* for constraint, & for borrow?

& for constraint, &\' for borrow?

the lifetime annotations are so we can elide incrs that would normally
happen when we return borrows or stick em in structs. but can this
scheme also catch errors at compile time?

can we prevent deleting something while a borrow is active? we\'d have
to somehow let the compiler track when something\'s going to be
deleted\... or maybe prevent moving it away while there\'s a borrow ref
active? that\'s probably the best we can hope for\... except it can be
moved by other references, crap.

it might be that the best we can hope for is just eliding increments and
decrements.

perhaps we should focus more on readonly regions for our guaranteed
safety.

perhaps we could have another kind type, besides instance/value/shared,
\"linear\", which followed all the rustish rules. doubtful we\'ll ever
want that though.

safecalling relies on saving a TLS \"handler\" and setting it, and when
theres a panic, unwinding the stack until we hit the handler. basically
exceptions\...

can we make certain functions note the function and line they were
called from? seems like itd be pretty easy.

offer debugging in vivem. have a way to print stack trace, args, and all
locals.

like in
[[https://gist.github.com/rust-play/214adf5bd15b18ec2d5c63d97ff8ba2c]{.underline}](https://gist.github.com/rust-play/214adf5bd15b18ec2d5c63d97ff8ba2c)

the interface is saying that the subclass has to have a certain static
method. dafuq? its like a concept almost\... weird!

dont move futures

want completablefuture so i can attach them to onready like i do in
cardfetcher for the cardfutures

thats why getcompleter should not be protected.

unless completablefuture just exposes it?

how will the lang server know when a better function is available for
overloading?

whenever we change a function\'s signature or rules, suspect lines are
ones that could see that function (imported its env) and used a function
of that name.

we\'ll need to recompile those.

so, every function depends on the rules and signature of every function
with the same name as one we call, in any environment we can see.

if an environment is a hashmap\<name, list\<func\>\> then we depend on
that list for every env we can see. luckily, we dont have to care about
the function body, just the signature and rules.

if we call out to a function and its return type changes, we need to
recompile ourselves.

if we call out to a function and it\'s an inferred return type and
\_anything\_ about the function changes, we need to recompile ourselves.

so really, we depend on the entire header, and if inferred, the entire
thing.

so perhaps every function can be memoized by these things?

we could memoize by line number too, then we would know what to feed
in\... ah, no, we just need to feed in all the functions with that name
in that environment. if there\'s any mismatch, then we know to
recompile.

we\'ll need to maintain a map of all the files who contribute to an
environment. that way we can quickly query all the files for what
functions theyre contributing.

theres going to be so much sharing of data here, that we really do want
immutability. we probably also want interning to speed up comparisons.
can we mark certain classes as interned? probably only nonatomics can be
interned, otherwise we need an atomic map, ick.

fun fact, interning only affects constructors.

interning will remove the ref when rc reaches 1, not 0.

chronobase would only help here if we were sharing atomically\... or do
we just want a mergeable arc\'d superstructure? yes, that. lets do arc
here. we can also arcify at the very end.

can we interleave or parallelize our == method? the cpu probably already
does for us as much as it can though\...

we should also not autogenerate == for things with floats\...

interface function can take an interface or struct to cast, or some
lambdas to make a new anonymous class.

integrate vivem with the lang server for faster run times like in
[[https://news.ycombinator.com/item?id=21259532]{.underline}](https://news.ycombinator.com/item?id=21259532)

should lazily compile because we dont know what functions we\'ll
actually need at runtime.

FutureSharer, is owned by a shared ptr

ShareFuture is a future which has a GetSource() which is a
sharedptr\<FutureSharer\>

FutureSharer has a Share which spawns an IFuture. it could spawn a
ShareFuture but i want to discourage share futures.

NewShareFuture makes a FutureSharer and returns a call to Share.

we can have the networkfuture as the inner future of a futuresharer, and
spawn a sharefuture from it to keep around in the stack. the stack can
hand out other sharefutures.

can we have a unique_ptr of the futuresharer? i suppose at that point
they might as well be onready observers on the original network future
though. huh, we could just do that. why arent we doing that\...?

Can we have a List\<#T\> and have it correctly track lifetimes?

lets say #T is an &&\'a Marine

it means a Node\<#T\> is a Node\<&&\'a Marine\>

which means the Node itself is locked to that lifetime\...

and the list contains them, so the list is locked to that lifetime\...

could work.

or, we can have regular constraint references actually be borrow
references, but with a \"runtime\' lifetime. then we can say:

struct Node\<#L, #T\>

where(#T = &&\'#L #X\> {

  val: #T;

}

and itll automatically work with borrow references, #L will just be
\"runtime\".

i kind of like the former approach. it makes perfect sense to me\...

we need to go all in on compile time borrow checking. itll be in a const
way. must be familiar to rust users.

default could either be constraint references or borrow references\...
lets go with constraint references since theyre more flexible.

& constraint

&\'r constraint with region

&& borrow

&&\'a borrow with lifetime

&\'r & borrow with region

&\'r &\'a borrow with region and lifetime

&wk weak

&wk\'r weak with region

&sh share

&sh\'r share with region

should be made clear that borrows are an optimization\... any serious
library should use them though. maybe. hmm.

a permission where we can only modify through the owned pointer, all
other refs are const. like rust but while we have a mut wr can also have
other const out there.

like rust, we can make a mut out of it, but cant alias that mut.

this ptr can be restrict, since its the only one thatll change it!

mapcalling:

let hp = getHp(\*marineList);

same as

let hp = marineList\*.getHp();

\<. operator will return the object, not the result of the function.
great for builders!

marineBuilder

  \<.setHp(8)

  \<.setName(\"Jim Raynor\");

can combine with mapcalling!

marineBuilder

  \<.setHp(\*maybeHp)

  \<.setName(\*maybeName)

maybe can do mut too?

marine

  \<.mut hp = 9

  \<.mut name = \"Jim Raynor\"

gross. lets not do that.

could use indices for weak ptrs and sharing perhaps\... if we have those
both, perhaps we really should have an object header or at least an
index. or maybe just a generation.

could we take the ptr, calculate what index it is in its page, and use
that index to look up in some table?

i wish pointers were just type+index, or type+index+gen. would be a bit
easier to know sizes\...

btw what do we do when a shared ref goes across a boundary? probably
just panic. means itll be using borrows under the hood probably.

shared stuff will suffer from enable_shared_from_this. need lang
support?

could we use generational indices for the chronobase? make it way
faster? in fact, the generation could even be the version maybe?

right now we put the version number in the hash map node (with the id
and the objptr) or we put it with the object itself.

we would need to figure out when an object is dead. maybe when an object
is dead we increment its version number to the future number? or we can
somehow reserve a bit for it?

a generational index would be a version number and an index\... huh
thatd work well.

but why not have just regular indexes, not generational? and have weak
ptrs just have lists of backrefs?

holy crap, a superstructure is a region\... and references into read
only regions are free.

but wait, we already elide ref counts mostly, to immutables\...

if we can ease the boundary between js and wasm thatd be pretty amazing.
superstructures and regular services could work nicely with that\...
we\'d also have to figure out good interop with js. having the
raw/native pointer for pointing outside, and handles for pointing inside
could work quite nicely.

need to figure out a nice way to attach listeners to handles\...
hmmm\... could build events into the language like c# did.

wait, dont need to. just have raw pointers to the outside world.
AddObserver would take in a raw pointer, easy. or better yet, the
platform could wrap a raw pointer in an owning object and hand that to
AddObserver.

we can call functions on raw pointers pretty easily\... in JVM we\'ll
assume it\'s a member of the first parameter, or we can just make a
static class to hold it all.

things outside can lock the thread by basically sending a closure in to
be called from the vale thread. since the vale thread can only do one
thing at a time, all references into vale are guaranteed not to change
out from under them. btw by \"sending a closure\", its really sending in
an ICallable or something, which has a \_\_call function.

how do we implement chronobase with the least amount of magic?

it would kind of suck to have reflection\... perhaps we can just have a
special kind of function that only accepts isolates, which can be
listened to?

wait, why cant we do this with every function? everything is an
isolate\... wait, except borrow refs.

come to think of it, ss funcs can accept borrow refs. hmmmm. maybe we
can register a virtual template translator? we can translate borrow refs
to IDs\... probably with a hashmap\<borrow, id\>. can we even do that in
jvm?

then we\'d basically use compile time reflection to do translations. not
sure if i like or dislike that. these functions could get large\... and
what would they return? dynamic stuff? gross.

thisll be tricky. there must be a way to do this nicely\...

maybe this really is the perfect candidate for a compiler plugin or code
generator. is there any best practices for code generation?

holy shit itd be awesome if we could do code generation for this. just
gotta hook into the compiler to deal with chrono refs somehow. would
have to hook deep into the templar probably\... and provide adequate
error messages and whatnot. man, if we could do that transform
post-templar itd be even better.

but the generated imm classes might be used by other modules\... ah, but
we compile module by module! we can do the chronobase first definitely.
except for templates, ugh. can superstructures have templates used from
the outside like that? jesus this can get complicated. what if we say no
extern templates? hmmm

maybe virtual templates are the answer to reflection?

if we didnt have weak pointers, then passing things to other regions
would be zero-cost in production mode\... sigh.

itd be nice to have linear types which could be passed through,
zero-cost. uni things, where we can only borrow in a lifetime scoped
way. cone style rather than rust style; things can change out from under
you.

in our coords, we have a param of region being readonly or rw\... can we
also do lifetime? rust does it, shouldnt be hard\...

then we can leave scoped behind because its not as powerful.

uni borrow owning constraint weak

region name

readonly vs readwrite

could it be said that borrow is another dimension? more in the lifetime
dimension?

uni owning borrow weak

regionname varlifetime

some sort of generic \"traverse\" method that will depth first put all
of the owned things in a list\... or perhaps a map\<type, list\<ptr\>\>.
you know, thats kind of like a sparse bunch.

or, it could take in a polymorphic lambda! how interesting that would
be\... itd probably explode and be stamped for a billion types but maybe
thats fine?

ick, i dont want to expose this to the user, its basically reflection
and bypasses privacy.

scoped ptr conpletely gets rid of borrow overhead\... but we dont need
it to elide increments.

so, the most expensive part of making a weak pointer is registering it
in the global table\... any way we can avoid or mitigate that cost?
elide it, defer it?

does malloc have a big roster of active pointers? if so, can we hook
weaks into that somehow?

we\'d probably only do this for native modules, not ones that compile to
whatever lang. hmm\...

could we just add an entry to an array when we make a weak ptr\... this
map would be per page or per type perhaps. then when we dealloc we can
somehow find all these entries that were added\...

could we associate a life count with the object? something that says how
many times its been allocd or deallocd? right there in the header. then,
when its deallocd (or moved), its incremented\... any weak that tries to
access it would see that their lifetime doesnt match.

we\'d still have to crawl a tree when sending over a thread, to
increment all these life counters. still, would require no modulusing or
global hash table\... but would have an int per object in which case
lets just go with the per obj vector.

if we crawl through all the owning pointers in a clique we\'re
guaranteed none are the same. this means its a perfect candidate for
simd!

go through all fields of a given struct to get any owns, simd lookup in
a hash map, simd get the table, add it to an array.

loop over each table, adding them to a huge array of things that we want
to null out.

then, simd set all them to null.

only crappy part will be crawling through interfaces\... will need to go
through vptrs ick.

ask on reddit what are some nice ways to do weak pointers, r/compilers

can we have ! cause an error to be returned? maybe that can be ! and the
panic can be !!

if only we could do that for &\...

\...huh we can with safecalling, into pure funcs. hmmmm\...

but then it would make things act differently in debug mode. maybe thats
fine?

struct MyStruct {

  hp: Int;

  name: Str;

  fn MyStruct;

  fn drop;

  fn deinit;

}

we could require those two things\... leaving them out would mean theyre
not autogenerated\... hmm

priv fn MyStruct;

could we use generational indices for our weaks?

obj\*

ind

or

ind

gen

ind

or

obj

ind

gen

ind

combining ind and ind to a 64b?

do we even need a gen? can we just do ind?

obj

ind in global table

ind in obj table

boom, no more modulus. no need for gens either cuz we eagerly null
everything out.

the table itself is an array of \[size, ptr\]. size=0 means empty in
which case the pointer points to the next available index.

much better than the normal control block nonsense with its atomics.

but wait, how do we map from objptr to index? maybe have a map\<ptr,
int\> but super slow\...

maybe bite the bullet and put into obj header?

if i have a class with 6 axes of freedom, why would i not use
if-statements? wouldnt i prefer not to have 64 different combinations of
the class?

fn for pure functions, proc for impure\... interesting. is that better
than an impure keyword? probably\...

pure funcs return objects\... that gotta be copied? can we merge two
regions costlessly?

how would we do arena calling? probably would need to copy. unless we
can somehow retrofit a normal page onto it? or, if an arena was divided
into 8b, 16b, 32b, 64b, 128b, 256b pages, we could just merge them into
the regular malloc system\... hmm\...

how do we allocate from a custom arena?

myRegion.new\<Marine\>(\"Jim\", 5, 4, 3);

easy enough\... would it be a compiler plugin or something?

also, we can have multiple allocators in a region, so maybe the two
features are orthogonal.

so\... wed hand in the allocator as a param! done.

wait, but we want to use arenas for a given func\... hmmm\... seems like
we\'d either need a srparate stamping for every func, or if-statements
at alloc call site, or inside malloc itself. hmmmm. this is only on pure
funcs, which could help

in a function that deals with multiple regions, one must specify the
region for every allocation.

when we call into a pure function maybe we can specify an allocator to
use for it all! since no objects can escape we can auto arena.

js bind goes away if you dont conflate all the \'this\'

wait, we no longer allow moving from something from inside a lambda,
revisit scout stuff?

instead of doing the C++ approach where hashmap is based on hashset, im
gonna go with the approach where hashset is a hashmap\<T, Void\>. the
reason is that i can constrain it so only immutables are in map keys and
sets.

\...or we can just make them completely different classes, would
probably be faster anyway.

really need a way to auto-infer mutability from members\... this is
getting annoying

forgetting a semicolon when the next line starts with = is annoying.

lets make a PartiallyFilledArray type, which is basically our arraylist,
but without the resizing. has size and capacity members. can zero-cost
turn it into an arraysequence. (maybe only when it\'s fully filled). can
also convert vice versa, assuming there\'s nobody with a reference to
it. useful for destructing.

can we convert mutables to immutables? freeze the entire thing perhaps?

our seq needs a .map0, .map1, .map2, etc.

i want to turn a \[Hamuts, Coord3\] into a \[Hamuts, ITemplata3\]

hamutsAndCoord.map1(CoordTemplata3)

now that we\'re only using : in patterns, we\'re free to use them for
things in expressions\... how about maps?

let m = \[\"hello\": 5, \"moo\": 9\];

primitives \< size of a pointer should be inlined into the union\...
like 32 bit integers!

memoize immutables\' hash codes in the other 32 bits of their RC
counter!

\*.ret will return something if it\'s truthy, otherwise give you the
falsy value.

\*.\*. double map operator, basically a double for loop.

the \"kind\" is basically a virtual static O_o

[[https://www.typescriptlang.org/docs/handbook/advanced-types.html#discriminated-unions]{.underline}](https://www.typescriptlang.org/docs/handbook/advanced-types.html#discriminated-unions)

\<T\> (a: T, b: T) { a + b }

things should generally take owning references instead of borrows,
because that opens the door to having owns wrap borrows, which will be
extremely useful for structural interfaces.

we should make it easy to put an owning reference into an owning
anonymous typeclass\...

lets try to do ifs without the parentheses. we can switch later if we
find a good reason we can\'t.

what to default implement for immutables?

hashcode

before

equals

Str

dstr

serialize

deserialize

traverse (can implement others with this?)

problem: we\'ll often be returning a struct, inside a function whose
return type is an interface, resulting in an upcast. however, when
there\'s no explicit return type, we don\'t know what we\'ll be
upcasting to\... which means we won\'t know what to put for the
structtointerface.

option A: require explicit type for the structtointerface, and pass it
down so we know what to upcast to.

option B: take out structtointerface2, and just make it implicit in the
templar phase. we can detect it and make it explicit in the hammer.

option C: somehow scout ahead for the return types?

option D: put in a StructToReturnTypeUpcast which gets its type from the
functions return. perhaps we\'d collapse the templar instruction to just
Upcast instead of Struct/InterfaceToInterfaceUpcast. it means that the
receiver (the if, the function return type, w/e) would determine the
type.

option E: use a full-on constraint system like scala so information can
freely flow

option F: do no casting, and if there\'s any conflict at all, then
require them to specify a return type. \<\-\-\-- great temporary
solution!

option G: get rid of the upcast instruction!

going with F for now.

we have the same problem with the two branches of the if-statement. if
statement \_can\_ just throw in some upcasts tho. anywhere else we have
this problem?

variadic args\... but can still just throw in upcasts tho. well\...
maybe not:

Map(

  (4, Marine()),

  (7, Firebat()),

  (8, Wraith()))

actually nm, that uses pack logic to flow the type back in i think?

making it implicit and a no-op lets us do covariance easier, since no
instructions needed to upcast and downcast right? hmmm it is needed to
go from struct to interface tho, crap\... and doublecrap, cuz we need
thunks because of the IA IB IC problem.

IA implements IB and IC

IA vtable is mathods a and b

IB vtable is methods a and b

IC vtable is methods b and a

so i have a function that takes in an IC\... can i cast it to a function
that takes an IA?

no because the vtable orders might be different. fuck.

we have share and borrow and raw because then share can spread through
the template rules like a poison type, being compatible with own and
borrow rules.

can we have a super thin pointer? one that points to an offset in some
sort of main memory page? basically, an index?

we\'d have to do some crazy mapping when transferring to another
thread\... maybe?

maybe would have to do complete copies? thatd be kinda unfortunate\...
but helpful with compaction\... can we do this as a zero-cost feature?

default rune type is coord. & will work on only coords, because it
changes the ownership and we dont want to assume the other aspects.

can still grab a kind from a coord with components, and assemble a coord
from a kind with components.

we should generally not cast from kind to coord. instead, we read
something as a kind or coord depending on context.

boom. done. beautiful.

OR what if we just make & only work on coords? thatd solve it too! no
need for defaults!!

how do we infer an own T from &#T

its annoying that we cant have handles that have borrow references into
ourself. since members get destroyed after container goes away, theyll
get cleaned up after container goes away, which is too late.

perhaps we need a deinit method? it can deinit all members\... set
options to null, clear lists\... we\'ll be doing that a lot anyway.

perhaps we can mark a field as \"internal\" to signal it was made by
self, and should be deinit\'d by self before destruction?

perhaps vale is trying to teach us to always use weak pointers when
pointing upward?

perhaps we can have a specific section of an object that has owning ptrs
to components which should die before destruction?

have a deinit method, which will conceptually reset the object? clear
lists, null out stuff. its the first line in any drop implementation. it
clears out all sorts of shit so that when we explode the container, the
ex-members are free floating and can be dropped themselves\...

ah, deinit shant call drop or deinit directly.

a drop will:

\- deinit members

\- explode self

\- drop members

would that work to sever all ties to container?

fn moo(a: &#T) { \... }

can have & take in both kind and coord, and not affect the rune\'s type.

we should also then default all runes to coord, otherwise we have to
specify like

fn moo(a: &Ref#T) { \... }

which is annoying.

or we can narrow it down to kind or coord, and between the two assume
coord?

cant do map.hasher(7), it thinks we\'re calling hasher(map, 7). crap.

we could:

\- require (map.hasher)(7). it\'s kind of nice because it makes it
explicit too.

\- preserve that ast deep into the templar, so we can figured out
whether its a field or a method.

concept Moo {

  fn moo(a: #T) #T;

}

can wrap a & in an owning\...

cant cleanly turn an owning into a & because that has a huuuge semantic
difference.

we generally want to not take borrow references to things\... because
then we cant take wrappers around borrowed references to things.
interesting.

maybe that solves the &#T problem?

if we really want a borrow then we can say\...

fn moo(a: Ref\[\_, &\]) { \... }

not too bad.

lets get rid of the let keyword! we can add it back in later if we want.
taking it out makes it more consistent with parameters.

the parser will have to be smart and look for the = somewhere in the
line, to give nice error messages.

what if we allow borrows and owns and weaks on immutables? and we track
them? and also consider them all equal

this means we wont run into the own != borrow problem\... but we kind of
still will. if we take one away then compare it itll still mismatch.

what if we keep share, and change how it compares to everything? it
would infect everything it touches\... could work.

we can have a destructor on shared objects\... itll just decrement the
internal ref count or something.

it\'s quite annoying to have to say makeOption()\^.get()

what if we switched it to makeOption()&.get()

makeOption!

we could do what rust did, and infer based on the receiving param\'s
ownership\...

maybe we shouldnt do shortcalling for %. we dont need it, we already
convert to superinterfaces. i also dont like the magic.

taking away something\'s destructor can\'t replace \@MustUse\... we want
the handle pattern to be able to have an automatically called
destructor, yet not dropped on the ground. so, we need \@MustUse.

ISomethingCallback\[capturedVar\]{

  fn \_\_call(this, blah: Int) { println(this.capturedVar + \" \" +
blah);

}(bloop);

shows two things:

\- we can immediately call an anonymous subclass if one of its methods
is \_\_call.

\- we can capture vars for anonymous subclasses!

\- since that first param needs no type, we can use it to access the
captured var

ISomethingCallback\[capturedVar\]{

  fn \_\_call(this: #T, blah: Int) { println(this.capturedVar + \" \" +
blah);

}(bloop);

shows we can capture the anonymous subclass\'s type with #T.

instead of \"overrides\" or \"for\", use \"impl\"!

how can we have lifetime annotations on virtual functions? apparently
rust can,
[[https://users.rust-lang.org/t/specify-lifetime-of-return-value-in-trait/22849/2]{.underline}](https://users.rust-lang.org/t/specify-lifetime-of-return-value-in-trait/22849/2)

we cant have templated virtual functions. so, we need lifetimes to not
really be template parameters. they need to include certain information:

\- whether the region is locked as read-only

\- where the region object is (for allocating, and to incr/decr
constraint counter, and map of weak pointers)

we can do this:

\- \'state could mean that it\'s a readonly region

\- \'!state could mean that it\'s a writable region

and then the ref to the region object could travel invisibly along with
the ref as part of the coord, or it can slip in as a secret parameter.
it\'s only on the stack so its fine for efficiency.

Swift:

\"try\" will just throw an exception if the result is an err.

try! will crash if the result is an err.

try? will return a null if the result is an err.

Rust:

? will early-return if the result is an err.

or, itll hit the surrounding try block.

cant do .(

because it appears here:

myResult\*.(n, y){ n == 42 }(50)

but\... we dont really need the above. and its kinda complicated.
disallow it?

Result could just leave its destructor out of its interface!

it could expose a visit though. and maybe a map and flatMap and expect,
for convenience, though they should just use visit under the hood.

this is equivalent to MustUse; the compiler doesn\'t know the destructor
so it can\'t automatically call it.

we should NOT use ? to convert results into nullables. people would use
it to throw error information away.

a component would probably be templated on the member offsets\... but
they can call into common functions which take the member offsets as
parameters, to reduce code size.

will save on pointers to other components, we can just kind of omit
them\...

borrow ref count inc/decrement\... not needed when something\'s passed
in.

is needed when we put something into a struct however.

just like callees lifetimes are guaranteed within caller lifetimes, do
we ever have structs where the member struct\'s lifetime is guaranteed
within the owning struct\'s?

components spring to mind\... DAGs within a parent struct. this is worth
exploring, could have big benefits, in memory and increment savings.

let x = tryAThing();

let y = x\|.tryAnotherThing();

let z = y\*.doSomething();

what if we want to feed something as not the first argument?

we\'d probably want that try block\....

let y = try { tryBlark(3, \"hi\", x%) };

but screw do-syntax. fookin nightmare eh.

maybe interfaces should by default expose functions that take in each of
the subclasses.

IUnit(myMarine) would basically be an upcast, a no-op.

that way we can use them with the % operator

ifuture could have \*required\* use of FutureCallbacks as a base class.
it would have helped with some guarantees.

how do we self impose that kind of thing? is there a good way?

using fn derefplz() Weapon {\...}

could be called \"computed subclass\"

is this useful?

do we need a space between (params here) and the { body here }?

seems to me that we\'ll otherwise never have an end paren and an opening
brace\...

if i wanted to consume a List\<Marine\> and get their HPs, would i\...

myMarineList\^\*.hp

thats crazy that we\'d have a \^\*. operator

when something goes out of scope, it will call the drop(\...) function
on it.

but it\'s unclear whether or not to check that no more references exist
to the thing at that point. it might be nice to just call the thing, and
not do anything trickier than that.

also, if we don\'t check that references exist to the thing, then we can
have constraint backreferences from inside the object! (such as a list
of components that all have constraint refs that point at the entity)

how do we know that None:Marine is a subclass of IOption:IUnit?

perhaps we need something like scala\'s covariants?

pure functions are just locking the main region. maybe impure should be
a keyword that means they modify globals and statics and stuff.

can turn an arraylist into an arraydeque but not vice versa. this is
actually kind of important in copying from one arraylist to another.
(turn the first one into an arraydeque, then consume from the front)

for now though, we can implement arraylists as arrays of options.

An \"exchange\" operation, which will allow you to assert that an object
has no references pointing at it, and null out all incoming weak
references.

  mut myObject = uni(myObject);

Called uni because the resulting object has only one pointer to it.

In release mode, on native, this will just null out all incoming weak
references, that\'s it. On JVM, this will produce a new object, copied
from the old one, nulling out the old one\'s field along the way (that
way, if there are any references to the old object, theyll still point
at that old one).

This could be useful for pools.

when we mutate an inl member, we have to call its destructor
in-place\... and then move the new thing in. this is terrifying, because
we might temporarily make the containing object inconsistent during the
old value\'s destructor.

we could\... move it into a temporary object, then destroy the
temporary?

also, do we really need to guarantee that when a drop function is
called, nobody\'s pointing at it? or do we just have to do that check in
the eventual final explosion?

what if we call destructor on the thing already there, then swap the new
thing into its place?

wait, wasnt a rule that we couldnt move out of fields unless exploding
the struct?

dont destructors explode their contents? how does that work with inl
members?

well, fundamentally, for inl things, we have two options for replacing a
member:

1\. assemble the new member separately, destroy the existing one, then
move new one into place (probably not gonna work if the outside has
references to it after its constructor). but if the old one\'s
destructor is what destroys it, then between that part of the destructor
and the move of the new one, there will temporarily not be a valid
object in there.

2\. move the old one out (not gonna work if it has references to it),
then assemble the new one in-place in its new home. HOWEVER keep in
mind, we usually move pre-assembled things into the new slot\... and
there will temporarily not be a valid object in there.

seems like no matter what we do here, there will be some invalid memory
there\... maybe inlining is inherently unsafe?

what if we just disallow swapping of inline stuff? in other words, make
them final?

what if we do some crazy magic that interleaves the destructor and
constructor of a certain field? no, would be hard to understand.

what if we only allow structs to be inlined? does it help to know the
specific destructor/constructor that will be called?

or, if we do allow mutable, we can do two moves at the same time\...
that would probably make a lot of people happy\... then run the
destructor.

Possible projects:

\- weak references. a first class ref type all the way to sculptors,
then they get their crazy hash maps.

we can put shared mutables into the stl by a thing having an owning
reference to itself (or putting it in a global map) and manually having
a different counter.

a shame to have that ptr to self though.

can we have something in the interface that says that address 0 must
contain a certain type? for optimization\... not sure if its wise tho.
maybe it is if its opt-in?

a module can be configured to be native or not. a class can be
configured to be an agent or not, which means it can be called across
the boundary. the compiler will build the JNI/whatever interop for agent
classes.

inline interface means that the objectptr part of the vptr+objectptr is
a whole object.

does this mean it can live in a register? dont think so, i think its on
the stack, and registers still have ptrs to it.

we don\'t need the async await transformation, we could do stackful
coroutines. goroutines are probably implemented this way.

but look into how pony does this, because apparently theyre blinding
fast

can we go from a mutable region and lock it to be readonly? i think wed
need to for speed\...

we can decrement a refcount when were giving our last ref to a
callee\... but then it would have to check at its end whether to delete.
we can save the checks by just keeping it alive longer.

have a way to expand capacity for an array at will, dont just offer a
standard vector class.

what happens if main region has multiple ways to get to another region,
and we lock it for writing, then try to lock it for reading?

probably a crash.

same with locking it for reading then locking it for writing.

can use \[\] for subscripts!

if theres a space or an open paren before, its a tuple.

if theres a name or a end paren before, its subscripting.

we can null out weakptr tables with simd! we\'ll probably want to handle
1, 2, 4 separately but after 8 we can do simd.

rust alloc: get table ptr (1, hot from param), deref it (1, maybe hot?),
check if a free int (if), get the next free integer in the list, or add
to the end. 2 cache misses? 3?

vale alloc: free

rust dealloc: free

vale dealloc: get table, modulus, get array, loop over it with simd
nulling

rust alias: free

vale: get hash table ptr (1, but hot), get size (1, but hot), modulus
(1), deref vecptr and size (1), add to end (1). 3, with some ifs and
fors in there.

rust deref: 1 miss to get generation, an if to check, 1 miss to get the
contents.

vale deref: free

rust memory (assuming boxes) is: ID+generation. extra 4 ptrs worth in
hash map entry (load .5)

vale memory: ptr+index, extra 2 ptrs worth in global map.

rust send: free

vale send: O(n)

since arraylists will be built into the language, we can have a global
table of them, and we can shrink them when we run out of memory or when
we are asked by the OS.

probably do them smallest to largest, so we can reuse things well and
theyll likely already be in cache.

a compiler switch that can compile a specific module to use unowned
instead of constraint refs, in case the library breaks. its api wouldnt
change, but the module would be slower

fn moo\<\'a, \'b\>

(x: raw rw inl Marine, y: \'b yon &!Firebat) {

  

}

kind

ownership: share own borrow

     maybe redundant? use own for shared?

permission: rw ro

location: inl yon

realm: global, realmA, threadB, mutexC, poolD

realm will have other information like where to put the borrow counts
and weak things

can use lifetime refs for pointing into pools, could make it easy to
correctly use them. A\* would def benefit.

to make something from a pool, we need to give constructors an optional
param or something\... or use a handle that says were using it? hmmm\...
could do it c++ stl style.

a stackish allocator is a special one that when something is deallocated
itll assert it was at the top then move the bump down again

java unique id can be determined by the thread\'s ID and the thread
local counter?

or we might as well do UUID at this point

to get an ID for a given object it \*has\* to be a member. that means
every java object needs an ID member =(

what if we allow multiple IDs for a given java object? just assign a new
one every time? no, we need to invalidate them whenever the object dies.
or, we can have an alive boolean in the java object, and a hashmap\<int,
weakref\> on the outside. thatd work.

CLR is a bit easier since we can pin objects.

wait, can we pin java objects?

 if A owns B owns C owns A\... would be a code smell? should we detect
it?

can we do multiple reads on the same mutex? or can only one person read
it at a time? if we do multiple reads then we have competition for
updating the borrow counts, dang\...

maybe we can do that similar to weaks? actually no, this is all compile
time checked, its fine, we never update counts when reading because its
all statically checked.

can we determine whether any references escape the lifetime of an
object? like in a binary tree? then we can eliminate refcounts\...

can we do a compatibility mode that will work with c++? maybe using
shared_ptr? or something similar enough that itd be fine

or maybe objects with internal pointers to weak lists, etc. that can be
used with regular ol unique_ptr.

objects outside and in will be pointing at the same box. must rewire the
internal ones to point elsewhere.

loop over all objs in the cluster. give them a new box. save all the old
boxes.

loop over all fields in the cluster. if they point at a box whose object
points back at them, null them out (theyre outsiders). if they point at
a box whose object doesnt point back at them, use the pointee objects
new box.

loop over all the saved old boxes, and null them out.

crazy idea, do we want to make the object-field-to-box pointer a jvm
weak ref? the incoming vale weak refs will keep the object alive.

and maybe even start the field out as pointing to an object that
immediately dies, so we dont have to ever have a null in there.

\...but then, wed have to check and maybe recreate it every single time
we point to it. maybe not worth it.

weak ptrs can be a mallocd integer, pointed to by a hashmap\<int,
int\*\>

can we reuse unused IDs? can we reassign ids on receive from another
thread?

if we use the nulling we might not need ids.

do we actually need unlet? can\'t we just get rid of a borrow reference
at its last use? and to unlet an owning reference, don\'t we just have
to move it? in other words, use it?

if we dont need unlet, then we have a nice reason to switch to the
let-less syntax too.

need a fast way to fill an array partially, and maybe have another
function that can fill the rest with something.

also need a way for an array to take a function that returns an error,
and it will destruct all of the already-constructed functions via
another function. the destructing function will not be able to return
anything, because it \_has\_ to finish. the destructing function is
totally free to halt the program, or store its errors in a list captured
by the lambda.

can we support publishing things? observables/livedata?

can we support publishing things in one blast, like publishers? would
need a superstructure (or a readonly superstructure) or maybe something
else?

how will we do per-thread replaying if we have mutexes? would we record
something somehow?

we might have to have whole-program replaying, and mutexes would save
the times (or thread IDs) that theyre locked and unlocked so it can all
happen in order.

Moving into a \"scoped\" parameter (which wont escape the callees) can
still be on the stack!

On that note, returning an object can eventually land in the stack.

scripting language specifically for unity development. meant to be on a
tiny vm. it will communicate with unity via an RPC-ish mechanism.

will have a lot of syntactic sugar for component-ish design, where we
can attach components to an entity on the fly.

called asteria.

in fact, specifically for roguelike development. show screenshots?

we only automatically call param-less return-less destructors. if
there\'s other destructors, we won\'t automatically call them.

we don\'t want to always make available the param-less return-less
destructor (that\'s as bad as C#\'s always requiring a param-less
constructor).

on a similar note, we also dont want to make available the downcasting
(the visit function).

perhaps we\'ll provide them by default, but allow them to opt-out. we
might need an opt-out keyword or something. maybe like c++, = delete ?
lol. or maybe it can be the same as unlet? maybe undef can be used for
both?

returning values from destructors, and passing params, is only possible
because vale has single-ownership. impossible in GC because it\'s called
by the GC. very awkward in RC because youd have to call it whenever you
let go of a strong ref.

what if we dont think in terms of blocks? or never release blocks back
to the OS?

every thread can have a free list for itself of things it can reuse. no
need to synchronize these.

perhaps threads can send parts of their free lists to other more starved
threads? maybe once we get to a certain capacity we can put them into a
central pool or something. or it can be an elaborate system of trades.

we would have to make sure we don\'t have any false sharing between
things, which means we cant have two objects on the same 64 bytes.
perhaps anything less than 64 bytes should just be copied from one
thread to another.

vale can also call into native, if dealing with an agent\... it will do
copying inbetween. doesnt need to be an agent, could just be a function
that takes/returns immutables.

if i have a &List\<Marine\>, can i cast that to a &List\<Unit\> ? since
& is wholly readonly\... it might work?

\...we might have to do a struct List\<+#T\> thing like scala\...

a weak reference to something in another thread is nondeterministic\...
we shouldn\'t allow something like that inside v, but it might be okay
outside.

so, how does v talk outward, and have a reference to something on the
outside? probably a strong reference, keeping it alive. hmm\...

perhaps we could explicitly wait for new calls from the outside (unlock
a mutex then lock it again, basically).

making interfaces to make all this easy would be nice

shouldnt have a hidden destructor method in an interface. should be
explicit. that way they can choose args and return types.

i wonder if theres a use for an interface with no destructor? presumably
youd match it first?

default enable wingman for native prod. default disable prod jvm clr.

agent handles in java can either be owning or borrowing. borrowing ones
will basically be weak\... no strong borrowing to be had here. will need
a dispose method. wont need marshalling.

in c# we can have things be regular c++ objs, but when we expose
something to the outside we wrap it in a managed object.

agent handles in swift can be strong or nonowning, nothin too ridiculous
here. can automatically dispose. wont need marshalling.

agent handles in js\... ick. how do we even. it can probably keep the
memory alive, but it will check the live boolean on every call. to keep
it from exploding we could null out member fields. wont need
marshalling.

agent handles across wasm will be tricky, we\'ll need a dispose method
probably. will need marshalling, dang\... or we can have a giant hash
map of ID to object? since destruction is deterministic that can work!
the IDs would be ever increasing\... can lazily assign them as theyre
exposed to JS. or can eagerly assign them on construction. i like
lazily, less memory overhead.

so what does something need to be an agent? i suspect any object will do
right?

an agent could be automatically generated. it\'s just a wrapper. in
fact, we can use agents in both sides. agents are the automatic
gatekeepers between the two worlds. from the outside, itll look like
we\'re just using regular things.

we should also have some sort of notion of an async service or
superstructure, to serve the native code use case. we\'ll use agents for
everything else.

agents should be weak pointers, and they should lock the receiving
thread. otherwise when they move across threads we\'d have to know which
thread to lock, and that\'d be annoying.

templatatype -\> kind

kind -\> valuetype

coord -\> reftype

or

templatatype -\> kind

kind -\> type

coord -\> coord

we should take void out of the language, and just use empty packs\...
and then alias \"Void\" to mean that.

the problem we ran into last time was: \"what does the destructor return
then? and if so, who destructs it?\" but we can get around that by
saying that the target platform should figure out how to destruct
immutables.

if you can mark an extern as deterministic then it wont record the
output in debug mode.

externs can be called but you have raw pointers to them\... dangerous
stuff. and itll get confused with agents. both are calling into external
code, so whats the difference? in my mind its that we use externs for
things we use, but agents use us. externs are for utilities\... or pre
existing code.

the distinction is a mess. gotta work on that.

rust needs unsafe to be flexible. vale does not. theres no tradeoff
there.

rust has overhead in hash maps, no overhead to alias. vale dereferences
for free, aliases rarely cost anything. rust aliases take more memory,
vale objects take more memory.

different tradeoffs, to achieve flexibility, speed, security.

can we have a modifier on kind, like arc, which makes it so we have to
copy a mutable or use a lifetime reference to it?

moo(4, 6) {

  \...

}

could mean this is the last param\...

moo(4, 6) (a, b){ mat \_ {

  

}}

moo(4, 6) (

  (a, :Success(b)){

  

  }

  (a, :Failure)){

  })

grouping lambdas with parens, interesting\...

i think struct members have to have their runes have \# in front of
them\...

it might help to require all runes be declared at the top\... since the
members might be so far below.

functions shouldnt have to put \# everywhere.

Inline lambda:

{inl\[x, y\](z) x + y + z}

it means that the resulting reference to this struct is inline.

or maybe ()inl{\...}

can we look up a function by return type? could be useful for
shortcalling, for finding constructors

destructors should be able to have arguments.

perhaps destructors could have the implicit keyword, to signal that its
okay to call them automatically when they go out of scope\...

lets have a prelim stage that types the templates\...

and lets make scout report up directionality of types unless we need a
solver for it

but wait, how do we do a templated call on a closure

let c = {\_ + \_};

c.\_\_call\<Int\>(3, 4);

how do we know that \_\_call accepts a coord param?

we can introduce it when we come across a lambda perhaps\...

or restrict \_\_call to only work on coords.

i like the former.

what if we have multiple \_\_call though? only templar can disambiguate

shit, we have that problem in general\...

we want things with same name with different rune types. + and \_\_call
are two great examples.

but still, maybe we can still figure things out in astronomer?

foo\<Str\>(myInt, myBool)

how do we know whether Str is a coord or what? do we have to know the
types of the handed in params? well, actually, they have to be coords.
but they themselves might be like Array\<3, Int\> and we might want to
disambiguate the call based on that 3. bit of a stretch\...

do we need info from the arg types to know which template we\'re
calling? not sure. i think so. imagine a foreach\... a foreach on a
vector might take a warp size template arg.

i think that means we cant know function calls\' rune types.

perhaps we can just do structs and interfaces and function rules, and
not function bodies?

if we dont need let, then we dont have to call it unlet\... unbind?
unset? release? forget? undef? undec? un?

a = undef

swap a = undef

null handling:

mat maybeX {

  () = \"hello\";

  (x) = \"hello, \" + x;

}

maybeX.switch({\"hello\" + \_}, {\"hello\"})

maybeX.map({\"hello\" + \_}) or \"hello\"

fn lookup(key: K) ?V {

  foreach elem in array {

    if (elem.key == key) {

      ret (elem.value);

    }

  }

  ret ();

}

if (x) = maybeX {

  

} else {

  

}

if maybeX {

  

}

\"explicit implicit null\"

perhaps:

if (x) = maybeX {

}

(dont need let if you have an = in there)

let (a, b) = (4, 5); will use a tuple.

wait\... the identifying runes have to be from the parameters\' top
level (the T and X in a: #T\<#X\>), the return, and the explicit
identifying ones. all the others are determined from those.

what happens when we \|. or \*. on an error? it should do that on the
contained value right?

%. can return-or. nice cuz its postfix.

%. and % return the falsy of the interface. uses hook functions.

fn truthify(

if packs conform to receiver\... the single element case will be a
nightmare for error messages

what if we require a comma at the end?

ret (myStr,)

ret (,)

what if we determine what they meant from context?

what if we detect whether they needed it, and if so, just use for
precedence grouping

what if we dont do it for params that receive simple primitives

what if we put a symbol in front for the call case

we dont need regular parens around a ret, or around an argument, or
around an expression thats about to go into a let. if we say that binary
exprs cant do packs (they need the precedence) then maybe we can pull
this off. binary ops will use the contents of incoming packs.

so\... operators of all kinds will take the values from inside parens.
things after =, or arguments to unary calls, or after ret, will be able
to autocall.

allow the user to define interface constructors

ret and % can return from containing function or containing free lambda.
maybe we should put fn in front of free lambdas?

fn\[x, y\](a){ x + y + a }

in fact maybe we need this to make captureless free lambdas

fn(a){ a + a }

if we have an operator that will return an error for us, what happens if
theres a problem in a destructor?

ah. lets allow destructors to return things, and allow MustUse.

(giving a custom destructor will set a special override for regular
destructor thats like c++s =delete)

in that case, we cant use the error operator, and must explicitly call
the thing\'s destructor, and manually specify what happens when theres
an error there.

if i call a function on an atomic ref to an obj, that has to call a
different function. crap.

we might need to figure something else out here\... maybe it can be
encoded into the kind itself? maybe it can be templated away like
lifetimes? we could do onion typing for it like rust? we could rely on
clusters?

leaning towards having clusters copy, but encoding it in the kind in
specific circumstances. an atomic kind can only contain other atomic
kinds or inl.

can we make atomic apply to the kind? we could then reuse kinds\...
functions would have to specify which they take.

inl rc and arc all require copying between them.

atomic vs regular rc only matters for yon.

make a mode where it frees two immutables whenever it allocates one,
instead of dropping the entire thing.

moo.map({\[3, \_\]})

wont compile unfortunately\... itll interpret that as captures.

though, keep in mind, we dont need captures for most simple things, only
things that live on.

moo.addListener(fn\[x, q=&a.y\](n, m){ n+q+x+m })

(a, b){

parens before braces are blocks. param lists are easier to parse than
expressions so try them first.

so, tldr, do the c++ thing, without needing the empty \[\]

it\'s actually not that hard to distinguish between a parameter list and
an expression; expression\'s don\'t have colons in them.

other good alternative:

\[a, b\]{ \_ + a + b }

\[a, b\]{ (a cross b).length() }

\[a, b\]{(x) (x cross b cross a).length() }

this is good because\... how often will we have a param-less lambda with
parentheses inside?

pipes?

{\|x: Int\|

what else can we use

what happens when we try compiling a lambda but it fails? are we doing
sfinae?

what rules must we add to only evaluate a function when were sure its
the right one?

perhaps denote a rune as output, not to be evaluated during overload
resolution

do concepts need return types?

a serialization layer between jvm and native could use a messaging
bridge that deals in IDs and stuff, and not send parts of messages that
are already known to the java side. when we send a giant message, we
send the tree in pre-order, and it remembers what has already been sent,
so it can skip those nodes.

or maybe the java side can have a weak ref cache, and have a
conversation requesting what it needs every time\... thatd probably be
slow though. hm.

maybe we can register and unregister certain objects, and both sides can
know to skip them?

perhaps we can keep track of what we\'re currently doing, with an
anonymous closure thing? a lazy evaluated whatnot.

we also want to represent a tree of successes and failures\... with
failures in this case having some extra data, such as the inferences
that were there at that time.

if we have an ordinary closure inside a templated function, is that
closure templated?

\...yes.

no.

yes?

no. in its environment, it is ordinary.

though, we should make sure that we identify it correctly, with its name
containing its parent functions template args.

cant express a pack in templata context!

IFunction\<mut, Int, ()\>

is that an empty coord pack, or an empty templata pack?

this means we probably should just do away with returning packs or
storing them, or anything that would cause them to appear there. packs
should just be a syntactical thing almost.

instead of returning packs from funcs, return tuples.

fn swap(a: Int, b: Int)\[Int, Int\] { \[a, b\] }

a ret in a block will make the block into an early-exit lambda, which
has to return an IReturnOr.

also, can we find a way to return from just the lambda? maybe ret vs
return? lamret? ret.0? retlam?

it would be nice to say:

let RuleTyperMatchContinue(conc30, mutabilityRuleT) =

  funcThatCanError(env, conc10, MutabilityTemplataType, mutabilityRuleS)

    or ret RuleTyperMatchConflict(conc10, \"Couldn\'t match against
mutability rule\", List(\_:RuleTyperMatchConflict))

can simplify:

let RuleTyperMatchContinue(conc30, mutabilityRuleT) =

  funcThatCanError(env, conc10, mutabilityRuleS)

    else ret (conc10, \"Couldn\'t match against mutability rule\",
List(\_))

so, \"or ret\" would need to know that it\'s handling specifically the
error case. we\'ll need some way to signal that something is the error
case. or\... it can try to put it into the let, and if it fails, then
puts the only alternative (the error case) into the \_

we\'d need to be able to construct an interface from just the
constructor arguments for its known subclasses. only doable for sealed
classes i think\... non sealed ones should have the call syntax for
making anonymous subclasses.

that underscore is a little wild.

let RuleTyperMatchContinue(conc30, mutabilityRuleT) =

  funcThatCanError(env, conc10, mutabilityRuleS)

    else {(e) ret (conc10, \"Couldn\'t match against mutability rule\",
List(e))

or perhaps

    else {ret (conc10, \"Couldn\'t match against mutability rule\",
List(\_))

most of the time we\'d require an \"else ret\" because its the only
reasonable thing to do\... but we can do this and allow a block that
ends in a ret.

let result =

  fold (list, initialValue) {

    do stuff;

    = thing to pass on;

  };

bucket sort:

myList.groupBy({[[\_.name]{.underline}](about:blank)})\|.value

scala:

myList.groupBy(\_.name).flatMap(\_.value)

take out owning refs and destructors. put em in with a few refactors at
the same time. my first patch.

dont allow conditional moves. move things from both if branches. no
moving from inside while loops.

let x: ?Marine = ((45, \"Raynor\"));

while (true) {

  doStuff(swap x = None);

}

swap wi

fn Array(n: Int, gen: #F \< fn(Int)#T) Array\<#T\> { \... }

how u like dat \< syntax

but really, we dont need it that much. one can put it in the rules.

IFunction is going to be difficult\... IFunction\<mut, (Int, Str),
Bool\> requires us to be able to have an expandyboi\...

interface IFunction\<#M, \...#T, #R\> #M {

  fn \_\_call(\...T\...) R;

}

and that\'s going to take a \*lot\* of work.

for now, we\'re just going to have IFunction1, IFunction2, etc.

packs are super, super risky and theoretical and can cause havoc. best
save it for another time?

it\'s hard to tell what goes on with:

let \[a\] = (4);

that (4) might decay to a 4, but this statement would only work if it
wasn\'t decayed.

for now, we\'ll have them decay because it makes things simpler.

in the future, we might be able to:

\- not decay it; decay it lazily. this ran into some troubles with
deferred expressions though, in the case of (&Muta(5)).x because that
temporary muta had to live through an &, a packing, and an unpacking\...
and we evaluate deferreds after packing. keep in mind deferring is
already quite complicated.

\- use context from above to know whether to decay. (&Muta(5)).x would
know that since it will be dot\'d, it\'s just a simple precedence thing.

\- use a different syntax: let \[a\] = (4,);

\- for destructuring packs, use let (a) = (4), which would be fine with
receiving a 4.

Map((4, \"hi\"), (6, \"moo\"))

we can do that because:

fn Map\<#K, #V\>(entries\...: Entry\<#K, #V\>) { stuff here })

itll just construct entry for each one!

if we put all the runes in the struct environment, then we can solve
backwards from destructuring! make special runes for mem0, mem1, mem2,
mem3, etc.

we can check the num members beforehand with an int templata but thatll
fall apart for structs that can contain lots of fields. probably best
just check afterwards for now

actually, we can do a greedy algorithm between the input templatas and
the member rules. kinda like regex

struct toRef\<#K\>

where { Kind#K\[mut\], Coord#C\[own, #K\] }

{ }

toRef\<Int\>:C

cfn toRef\<#K\>

where { Kind#K\[mut\], Coord#C\[own, #K\] }

= #C

cfn toRef\<#K\>

where { Kind#K\[imm\], Coord#C\[share, #K\] }

= #C

#C = Ref\[own, List:#C\]

we can figure out here that #C is a ref. however, with the new system we
cant, since it requires that the entire tree be understood.

we can change it to have 4 outcomes perhaps\... conflict, unknown, know
type, know whole subtree.

or we can build up a parallel tree of typed stuff but thats gross. and
basically comes out to the same thing.

Perhaps we dont need to say MyList.Elem.kind() anymore

We needed those parentheses to disambiguate accessing a member of the
class (Elem) and getting the kind.

but if we use colon for accessing member of the class, we can just
say\...

MyList:Elem.kind

BOOM. DONE.

can we do the inferring everywhere? since every function has to have all
its inputs declared, can\'t we use that syntax even in rules?

i dont want to say \^MyList:\*MySome:\*Int in rulexes. we should be able
to say the right thing\...

\...but i worry about #T= MyList:MySome:Int. How will it know that it\'s
a kind?

And if we do MyType.SomeRune = MyList:MySome:Int how will we know what
to coerce to? there\'s no intermediate rune\...

perhaps that\'s where we use something like\...

MyType.SomeRune = Ref(own, yon, rw, MyList:MySome:Int)

or

MyType.SomeRune.kind() = MyList:MySome:Int

instead of using magic prefixes like \"\_\_ParamRune\_\" consider having
a sealed trait that has two subclasses: UserRune and AutoParamRune.
consider this for all magic prefixes used everywhere.

we need to say MyInterface(&x) to cast that borrow to the right type.
but we also want to say MyInterface({stuff here}) to make a new
MyInterface subclass.

in fact, we might want to make a forwarding wrapper with that first
syntax.

lets be consistent, thats the way to make a new instance. lets use this
to cast:

as\<MyInterface\>(&x);

or

as\<&MyInterface\>(&x);

btw, x.as\<MyInterface\>() implicitly borrows\... people might want:
x\^.as\<MyInterface\>()

or, we can say that x\<MyInterface\> will turn it into a MyInterface.
dunno if that has any syntactical ambiguities, like with
(x\<MyInterface\>)(3, 4);

how do we make anonymous subclasses?

MyInterface(\[a, b, c\], {(this, x, y) this.a + x}, {(this) b + c})

MyInterface{\[a, b, c\] {(this, x, y) a + x}

MyInterface({(this, x, y) a + x}, {(this) b + c})

if only there was a way to not need those this\'s\...

MyInterface({a + \_}, {b + c})

yeah, we need that. also, since packs are a thing, we can just say:

({a + \_}, {b + c})

and hand it in to a parameter expecting a MyInterface.

though, thatll be a bit confusing perhaps.

if they need \'this\'\... they can make a full struct. if they need to
mutate they can do boxes manually.

another idea:

MyInterface{\[a, b, c\](this) {a + \_} {b + c}}

might fit with match\...

add a note how expressiontemplar parses templatespecifiedlookups.

maybe we should allow templatespecifiedlookups only when they result in
one unambiguous function.

maybe we can also allow specifying the param types:

moo:Int

moo:Int(:)

moo(:)

moo(:Int)

forEach(myList, print:Int)

i cant see us needing much else. in expression contexts, were only
looking for functions, not structs and interfaces and stuff.

for now we can say:

fn(Int)Marine({ Marine(\_) })

itll be translated into

IFunction:(mut, (Int), Marine)({ Marine(\_) })

and that will make a new owning thing, which contains a borrow ref to
that closure.

or, we can make these naturally implement that IFunction. thats probably
easier\... yeah\...

which means we\'re blocked on the rule solver.

also, for the pack thing, just look for the function with the right name
and the right args, and grab the struct from the return value. done.
easy hack.

some calls will automatically borrow\... all binary calls, and some
unary ones?

When we do constraint solving, we\'ll start with a few concrete facts
(#\_\_Param0 = \*Int, #\_\_Param1= \*MyStruct:\*Str) and hopefully
figure out some other runes (#X, #Y, #Z).

It could be that when we use the facts, we might conclude that we have:

\- 3 possibilities for #X

\- no idea for #Y

\- 1 possibility for #Z

At that point, make the assignment for #Z. Then we do it again, and
find:

\- 1 possibility for #X

\- still no idea for #Y.

Do the assignment for #X. Then hopefully we have 1 possibility for #Y.

Let\'s do that simple algorithm for now. We can do a more complicated
one later.

If we ever get to a stalemate, where we have 4 possibilities for #X, 4
for #Y, 4 for #Z, then give up; require user specifying. It should be
good enough for now.

temporary solution is to propagate constraints everywhere into a map,
and use the current approach to solve, but checking against all
constraints before actually registering the inference

write note saying we have different ASTs for pattern templexes and rule
templexes because former does coord-centric thinking and latter doesnt,
and keeping them separate prevents accidentally conflating the two

sometimes could have rules:

thing isa otherthing

but before we can evaluate that, we HAVE to know of all possible edges.
that means we CANNOT introduce edges willy-nilly, such as in function
environments. they all have to be in the top level.

but\... that means we can\'t create anonymous interface subclasses.
dang.

OR, we can do it based on the currently known edges in the current
environment. of course, since that rule is probably in some environment
in the boonies, we\'ll have to use the environments from thing and
otherthing. that would actually work.

game.units

/.{\_.level == desiredLevel}()

\|.maybeWeapon

\*.durability

\*.{\_ + 7}()

.sum()

add7(myList)

myList.add7()

myList\*.add7()

add7(myList\*)

\* spreads?

why do we call it main:lam1? why not just call it \_\_call ? it\'s
disambiguated by its first argument\...

yep, forward declare the struct.

dont put paths in the temputs, because we can have any number of
environments for one template function.

or, hoist the functionS

map of structref to ienvironment. hook it up after making the struct

first, make a structref.

put it into a new environment

then put the functionS into a functiontemplata with that environment.

then, put the functiontemplata into yet another new environment, dont
evaluate it.

associate this last environment with structref in temputs.

if ordinary, compile function.

the point is to have an environment that contains the struct plus the
functiontemplata with the environment. its circular\... this is as close
as we can get. the function wont see itself in its environment\... maybe
we should have a rawfunctiontemplata with no env? maybe we should have a
templatawrapper that comes with the env it came from? maybe we can have
a unique ID for this function?

yes\... if we grab something out of an environment, we could totally
grab the rest of the environment too. just put a structref or a
functionS into the env, and when we grab it out of the env, we will take
the env too. but jesus, we cant jam that into a struct field or a return
type\... ah, but when we do, it will be a concrete stamped thing, and we
can map it to an env in the temputs! THATS THE KEY! do what we did with
the old global env!

\^ ANSWER

lets separate ordinary and template case. for ordinary case, put struct
into env, put the banner into env, compile the function, easy. for
template case, put the struct into env, give that env to
functiontemplata\... before compiling, throw a banner into the env.

UHHH path cant be a list of strings! has to be a list of templatas
perhaps. or just a templata. otherwise, how to distinguish between two
different stamps of a function?

maybe a templata then a unique number.

in templar we often ask for the origin of a function\... what are we
getting from it? can we just store that instead of the original
function1?

with UFCS, we \*have\* to look through the environments for all the
parameters\' types. otherwise, we\'d have to fully qualify every time.

Marine::shout(marine); instead of shout(marine);

That\'d be annoying.

look up vtable of the pointee of a user smart ptr?

like how rust has

fn myFunc(self: Rc\<Self\>)

someday we might want to receive packs into packs\...

for example to do:

  fn doSomething(x\...: \...#T) \...#T { \... }

  let (x\...) = doSomething();

lets add a test for it

for selecting an equals overload, we could do something like:

fn(Int,Int)Bool(equals)

fits nicely with Int(y) and Bool(x)

or would that wrap it in an interface subclass? it would probably wrap
it in an interface subclass. hmm. well, maybe that\'s fine, since the
particular subclass is still known statically?

can i do the opposite of an if-let?

if (let List(a, b, c, d) = something) {

  \...

}

could i instead say\...

let List(a, b, c, d) = something

else {

  throw new Exception(\"bad!\")

}

// now a b c and d are in scope

cant we make a special case for when something appears before a \[ ?

Ref\[\_, \_, \_, Int\]

that way, we dont need to have a : in front of it.

it means we cant do something like:

MarineList.ElementType\[\_, \_, \_, #K\]

but do we even support that? how about we dont. we could instead say

MarineList.ElementType = Ref\[\_, \_, \_, #K\]

or

MarineList.ElementType Kind\[mut\]

Components Rule Wants Type

CRWT

Components Rule wants to know what type it\'s got, so it\'s easier to
deal with in the infer -evaluate layer. This restriction could be lifted
later if we want to do some extra fancy mega inference.

Might eventually be nice to say #T\[\_, \_, \_, \[mut\]\] when #T is
used in atom (and therefore known to be a ref)\... then again, we have
isMut(kind(#T)), which is more readable.

Find a way to have as-yet-unevaluated things in the environment, which
we can stamp. We might already have this\... the as-yet-unevaluated
things are the banners. But right now, we\'re using StructRef2s for
this\... reconcile that. It\'s especially weird that it\'s its own
terry.

environments can be identified by a pretty simple scheme, just a bunch
of strings really. (\"IncendianFalls\", \"main\", \"block1\")

(\"IncendianFalls\", \"main\", \"lam1\")

(\"IncendianFalls\", \"Game\")

We can have these in the templatas and not feel weird about it. WHICH WE
WILL NEED, because the environment for something affects what functions
are in scope and therefore which overload we might select for something.

we can do templates with \<\> if we pay attention to spacing.

x \< 5 is using less than

x\<Marine\> is templating

because spaces.

Think of a better term than \"kind\"\... it seems it\'s already used
(like higher-kinded types)

class?

sort?

or we can say fuck them, and use kind.

What will we use for a namespace separator?

Dot might be hard.

namespace FlamingJustice {

  fn func:#T() { \... }

}

fn main() {

  FlamingJustice.func:Int();

}

we might misinterpret that as a method call.

Perhaps apostrophe? FlamingJustice\'func:Int();

Perhaps double colon? FlamingJustice::func:Int();

\...kinda digging the apostrophe\... besides, it implies ownership and
is often used in names, kinda convenient.

interface Printer {

  fn \_\_call(x: #T)Void;

}

what does it mean to extend that virtually? I havent a clue. Maybe that
isnt a virtual function at all? Maybe this is saying that anything that
implements this interface must have this template\... but we cant call
it \*through\* the interface. So its a contract.

We could instead say

contract Printer {

  fn \_\_call(x: #T)Void;

}

It cant be isa\'d.

but wait\... when we stamp those templates\... wont they need their env?
like if they\'re in a namespace with all sorts of private functions, or
in a struct with private functions, or something

What happens when we don\'t specify runes in the identifying runes?

fn sum(a: T, b: T) T

where{ let T: Ref; }

{ \... }

It\'s somewhat reasonable. Perhaps if there\'s no identifying runes, we
should generate the order by what appears first in the params (then
return val? not sure).

when we say a type in expression context, like Marine(4, \"Raynor\"), we
should translate that to constr:Marine(4, \"Raynor\")

The existence of constr could simplify things.

For example, we want to do:

fn construct(t: &T, args\...: Args\...) R

where { let R:Ref = exists(constr:T(Args\...)); }

{ println(\"constructing trivially constructible T\"); }

it\'s hard to say how we\'d do that without constr. It\'d be something
like\...

exists(T(Args\...))

which seems weird. or is it? Is it weird that we have both a function
and a type as the same name?

It kind of is. it makes it ambiguous, what will the templex \"T\" be?

this rule syntax seems pretty good\...

all of these are valid:

fn moo:(T, E:Kind)

(a: T) Int

where { T\[\_, \_, \_, E\]; }

{ \... }

fn moo:T

(a: T) Int

where {

  let E:Kind;

  T\[\_, \_, \_, E\];

}

{ \... }

fn moo:T

(a: T) Int

where { T\[\_, \_, \_, let E:Kind\]; }

{ \... }

use \"asa\" or \"as\" or \"for\" or \"impl\" instead of \"overrides\" ?

struct MyThing { }

MyThing isa MyInterface;

fn moo(this: MyThing as MyInterface) { \... }

struct MyThing {

  isa MyInterface;

  fn moo(this: as MyInterface) { \... }

}

struct MyThing {

  isa MyInterface {

    fn moo(this: as) { \... }

  }

}

if in an impl block can we leave off the \"overrides IMoo\"? can we
infer?

what about \"\<\"? itd be consistent with rules.

struct MyThing { }

impl MyThing for MyInterface;

fn moo(this: MyThing \< MyInterface) { \... }

struct MyThing {

  impl for MyInterface;

  fn moo(this: \< MyInterface) { \... }

}

struct MyThing {

  impl for MyInterface {

    fn moo(this: \<) { \... }

  }

}

we\'ll call it a lifetime ref. &\'a

the point of a lifetime is to be able to operate on two realms at once.
my thread\'s data, and that mutex\'s data. we know when we go from one
to the other.

fn moo(x: Marine, y: &\'m List:Marine) { \... }

this example means y is in the mutex\'s memory and x is not.

when we move the marine from our mem to the mutexs mem it should notice.

y.add(\'a x)

fn add(list: &!List:#T, item: #T) \...

yet we\'re calling it with two different lifetimes. we\'ll infer that we
want to use that first arg\'s lifetime. so at the call site we\'re
moving from one region to another. thats where we move between regions,
hence the \'a, to make it explicit.

a vtable is just an optimization, a clever way to handle virtuals.

what\... is the non-intrusive way to do it?

i suspect a lot of switch statements\... or a function that loops over
all functions under that name. we must think of all this from that
angle.

hopefully we can arrive at vtables, but through thinking on how to
optimize the loop thing.

we need to look for things by return type\... so we can do:

struct Marine {

  hp: Int;

  priv strength: Int;

}

fn toSeq(this: &Marine)\[Int\] = \[this.hp\];

match myMarine {

  \[hp\] = println(hp);

}

perhaps we can do it via rules?

fn toSeq:Int#N

rules(#N = 1)

(this: &Marine)\[Int\] = \[this.hp\];

\...gross\...

actually, we shouldnt allow overloading by return type. there will only
be one overload.

how do we return an optional seq here? a function called match?

fn match(this: Marine, x: IMySuperclass) ?Marine

rust is too constraining.
[[https://stevedonovan.github.io/rustifications/2018/08/18/rust-closures-are-hard.html]{.underline}](https://stevedonovan.github.io/rustifications/2018/08/18/rust-closures-are-hard.html)

Can select an overload like this:

moo(:) will select the one that takes in nothing

sum(:Int, :Int) will select the one that takes in two ints

this is consistent with how \[=\] is a map.

also,

\[:mut 4, 5\] can be a mutable tuple with 4 and 5.

\[:mut =\] can be an empty mutable map

we should have a convention that all types and template functions are
uppercase. that way in template rules we can use things like mut, imm,
rw, ro.

but\... what happens when we want to use a function as a templata\...
crap.

TreeMap:(Location, Int, locationLess)

okay, this is a reason to say that all functions should be uppercase
too.

TreeMap:(Location, Int, LocationLess)

now we can use mut, rw, ro, etc.

its a convenient trick\...

lower Ref#R\[own, inl, rw, Marine\] to #R = Ref\[own, inl, rw, Marine\]

we want various functions like owns(#C) borrows(#C) shares(#C)
points(#C). and because UFCS is awesome, #C.owns(), #C.borrows(),
#C.shares(), #C.points().

#C.isInline(), #C.isYonder()?

#C.loc() = inl

#C.kind().mutable()

#C.kind().immutable()

we could do what we do in patterns and allow underscores in calls, such
as isImmutable(\_)

Ref#R\[\_, \_, \_, isImm(\_)\]

Ref#R\[\_, \_, \_, ==(mutability(\_), imm)\]

Ref#R\[\_, \_, \_, mutability(\_) == imm)\]

Perhaps we should split hammer into two phases\... hammer A will be like
the templar but no addressible members. hammer B will be flat
instructions, ready to go into llvm.

right now we use all the runes in the rules to make the identifying
list. perhaps someday we can figure out what truly makes something
identifying? so we dont have to spit out so much into the mangled names
in llvm.

with weak pointers you have to chain them together because at any point
your program could delete it. you gotta be ready for it to disappear at
any step.

its really quite unavoidable\... cant do much about it. strong refs help
simplify the thinking, but can cause crashes.

We might need the specified/coerced distinction if we allow template
rules to do inferring\... otherwise we might accidentally sneak things
of the wrong types into a signature or something

struct Thing:#T {

  t: #T;

  Thing(t: #T, from: #A)

}

Thing is a function that combined its own rules with the rules of the
struct.

\"theres nothing we can do in the language, there\'s no possible logic
about when the language destroys the object, that will guarantee all
strong refs point at an allocated object that is within its own
\*conceptual\* lifetime (unless we\'re fine with crashing the app)\"

match x {

   \_ \* 4 + 6 == 10 { \... }

}

is like:

match x {

   ==(+(\*(\_, 4), 6), 10) { \... }

}

notice how the \_ is deep within. that\'s actually\... fine!

maybe we shouldnt use \'if\' inside match statements

match myThing {

if :Marine\[x\] where x.between(5, 6) { \... }

}

because id rather have the \'where\' be \'if\'

match myThing {

    :Marine\[x\] if x.between(5, 6) { \... }

}

match myThing {

   if Marine(==(\_, 10)) { \... }

}

equivalent:

match myThing {

   if Marine(\_ == 10) { \... }

}

equivalent:

match myThing {

   if Marine(\_.==(10)) { \... }

}

can we capture it too?

match myThing {

   if Marine(x.between(5, 6)) { \... }

}

hmmm\... i dont see that working\...

well, we can always put it in a where clause:

match myThing {

if Marine(x) where x.between(5, 6)) { \... }

}

async await transforms code, chronobase transforms code\... would they
be similar?

mut numFives++;

uh oh. if we have all sorts of long stuff in the captures and param list
for a lambda, we might not want it on one line.

{\[reallyLongVariableName, bReallyLongVariableName,
cReallyLongVariableName\]

    (myParameter: SomeTypeName, otherParameter: OtherTypeName)

we\'ll probably want to put the \] and ( right next to each other. not
too much trouble.

{\[

    reallyLongVariableName,

    bReallyLongVariableName,

    cReallyLongVariableName

\](

    myParameter: SomeTypeName,

    otherParameter: OtherTypeName)

  \...

}

while we\'re evaluating coord rules, we might be calling templates.

fn repeat

rules(#R = List:#T)

(elem: #T, int n): #R {

   (makes a list of n #T\'s)

}

To call templates while trying to evaluate something is a \*very\* bad
idea. We\'ll be stamping a massive amount of bad code.

We need to instead figure out a way to know ahead of time whether the
thing is good. We probably need to switch to a concepts/bounds approach.
Look into how go, rust, and c++ will do it.

fn(Int,Int)Bool

fn(Int,Int)(Int,Bool)

fn(Int,Int)fn(Int)Bool

how do we distinguish between an std::function and something that\'s
callable? keep in mind we can convert anything to an std::function.

callable(

a \'perf\' keyword on functions (and/or blocks?) when present it will
tell you to have explicit inl vs yon, explicit mov vs cpy

InterfaceCall2 and ExternCall2 are both always wrapped in their own
Function2.

This is really nice, because every call produced by CallTemplar2 is a
FunctionPointerCall2.

it\'s required that we have Argument3 nodes at the top of every
function. this is so we can refer to arguments by registerID.

it might also be required to stackify them, because only things on the
stack can be named.

perhaps we can automatically do this, to simplify tablets?

perhaps we can simplify the rule to be that only mutable named things
have to be on the stack?

we pass things into functions incremented, the function is responsible
for decrementing them

LoadExternFunction3+Call3 was changed to CallExternFunction3 because
extern functions dont obey our reference count rules. we normally
increment things as we pass them in and let the callee decrement them,
but extern functions cant be expected to do that.

extern calls don\'t have to increment or decrement their args. they
otherwise have to obey our increment rules. if they make an integer in
their body and return it, it\'s incremented because it\'s in the return
slot.

must add a check somewhere to make sure that a SoftLoad3 from an own
clears the register. in other words, it must be doomed by this register.

must add a check somewhere that a Stackify3 from an own clears the
register. in other words, it must be doomed by this register.

also a check that a StructToInterfaceUpcast from an own clears the
register.

currying templates is proving weird. isnt there a better way to do this?

i want to be able to say something like this:

sort(myListOfInts, \<)

which means we need some sort of template like this:

fn sort

:(#L, #C)

rules(

  #L = List:#T,

  #C = fn(&#T,&#T):Bool)

(l: &#L, c: &#C)

: #L {

  let arr = Array:T(l.len(), l.iter())

  sort(&!arr, c);

  ret #L(arr);

}

the important part is that we never just take in a templata, we must
always know what kind of templata it is.

we shouldnt allow doing .mutability, .ownership, etc in rules.

i want to be able to say #C.ElementType in here:

fn sort

:(Collection)

rules(

  #C \< RandomAccessCollection,

  #T = #C.ElementType)

(c: &Collection)

: Collection {

  let arr = Array:T(len(c), {c.(\_)})

}

perhaps we can instead get #C.mutability by saying mutability(#C). and
remember, we can always destructure it.

lets also have Array function take in a param-less lambda. just call it
N times, it\'ll just work. why? so we can throw iterators in there!

how would we do slices?

easy just use borrow pointers. actually, itd be more like a lock pointer
here.

how do we do locking?

have a global hash map (like the weak map) and on any modification to
that struct or array we check to make sure theres nothing in the global
hash map.

this would be pretty expensive\... its helped by branch prediction, the
vast majority of the time we wont get a hit in the hash map. we should
make sure the load factor is \<.5 to ensure it.

also, since counts are nonzero we can use a specialized map thats just
addr-\>count and the count could be zero.

how do we enforce the nonzero thing? perhaps an interface. it requires
that the underlying struct has something thats nonzero. how to enforce
that?

actually, pretty easy. a setter can check to make sure the incoming
value isnt zero, do some or\'ing with the last bit, and set it. this can
either be a wrapper, or a keyword. would be nice as a keyword\...
compiler plugin?

we should make our own allocator, since we know that mutable data is
only accessed by one thread at a time. makes allocation for us much
faster. we need to support moving ownership of one chunk of memory to
another thread. difficult, as our pages will have multiple threads
owning the things inside. allocating can be easy, we\'ll just have a
bump pointer that eats up memory. whoever can allocate into a page will
have ownership of the page. anyone can delete from the page at any time,
but only one can allocate to it. we\'ll have to figure out where in the
page the metadata would be, and deleting would just (non-temporally?)
write a zero to it, to signal that it\'s not used anymore. the
allocating thread can stop when it gets to a one, and keep skipping
until it finds a zero.

so, who would watch for when all the things in this page are zero then?
hmmm\...

why not just have an array pointing to the final nodes? they have their
version in them right?

\...oh they dont. can we have a big array of pair\<version, T\>?

might be tricky and exlensive for struct types. worth looking into tho.

could we use chronobase\'s copy-on-write capabilities for other things,
since it does it anyway?

since chronobases are their own modules, we can have functions inside
the chronobase which do the fancy root-pointer-is-first-arg trick!

if something is used in the chronobase, it needs to be compiled as
chronobase.

we already know we can only depend on other superstructures. but, isnt
this interesting, the chronostl has no root! it has the guarantee of
isolation though. kind of. after template stamping, it actually does
have pointers to the outside. and it doesnt use globals.

perhaps not using globals is enough? and doesnt use anything
nondeterministic. basically pure.

in other words, utilities; no globals, and is pure.

we can pull something in if:

\- none of the module\'s functions pull from anywhere global or
nondeterministic.

\- it references no types defined outside of itself or its dependencies.
templated types are fine though. (but how can it reference a type
defined outside of itself or dependencies?)

i think it just comes down to: the functions cant pull from
nondeterministic functions or globals. and really, all nondeterminism
comes from globals anyway, so its just globals. in other words, all
functions must be pure.

so, a chronobase can depend on any module, if all of its functions are
pure. we just need to mark a module as pure and we\'re golden.

from one perspective, a vtable is a horrible imposition on the object
(or the pointer in the case of fat ptrs), it\'s saying \"hey, instead of
describing what kind of thing you are, and having me do the mapping, im
going to jam some weird stuff (function pointers) into you so i dont
have to do that mapping\"

it\'s pragmatic af but lets not forget that it\'s a pretty invasive
coupling.

come to think of it, this invasive coupling is the entire foundation of
OO, lol.

When someone makes a suggestion to Vale, ignore their proposed solution
and instead try and figure out, whats the pain point theyre trying to
address? And then go further: what is the class of pain points they\'re
trying to address? Don\'t just fix it for the one instance, fix it for
the class. If the fix is good, then \*consider\* adding it to the
language.

in the thing we made our chronobase as a code generator but we didnt
have to. we could have put it in the compiler phase to output VIL.

so lets say we\'re doing valefire\... would it be a different compiler
or code generator? or both? i think we\'d use the same compiler as
always, and then do a special code generator for it. or rather, we could
have done the compile thing, and have it use some lower level hooks like
what firebase would have given us, but type safe.

it seems we have a lot of flexibility here. how do we know when to
compile something and when to codegen it?

we probably should err on the side of compiling it. better for testing,
simulating, we have better guarantees with VIL\...

a pack is an amorphous blob that conforms to the shape of the receiver.

one awkward part: what happens when we try to jam a 4 into List:Int?

what about (4) + (6) ?

we could allow downgrading from (4) to 4 but not upward from 4 to (4).
that kind of works.

if you have a (4) you probably mean for it to go into a list, but what
happens when you accidentally put it into an integer? thats a risk.

only solution i can think of is to put a comma at the end\... it would
signal\... what? that it can be turned into a list but not an int? too
specific.

from a certain standpoint it makes perfect sense to allow (4) to go into
both an int and a list:int\... perhaps we can just bear it.

what if we wanted to have extractors with arguments?

marine mat {

  if Marine(between(0, 5)\[hp\], name) {

  }

}

between(0, 5) is calling a function, and the \[hp\] is capturing.

if only we could call an isZero(Int):Bool function like this\...

marine mat {

  if Marine(isZero, name) {

  }

}

but it would be ambiguous with binding to var named isZero.

what if we changed them to be:

marine mat {

  if Marine(between(0, 5, \_)\[hp\], name) {

  }

}

marine mat {

  if Marine(isZero(\_), name) {

  }

}

that could work nicely.

VML is vale markup language, it\'s a subset of vale.

Import(\"dev.vale.superstructure\")

Module(

  deps = (4, 5, 6, 7),

  compiler = Superstructure(root = \"App\")

)

it\'s a bunch of expressions like that. the top level is a list of
values.

it\'s all statically typed:

struct Module {

  deps: List:Int;

  compiler: ICompiler;

}

interface ICompiler { }

struct Superstructure {

  isa ICompiler;

  root: Str;

}

struct Import {

name: Str;

}

interface IModuleThing { }

Import isa IModuleThing;

Module isa IModuleThing;

This module file is a list of IModuleThing.

Note how we\'re feeding a pack into the deps field right there. packs
are amorphous and only have a form when theyre received, so this is
basically calling the constructor of List:Int. This should be similar to
how Vale works.

funny, the only things we need to specify types for are things that
we\'re passing into an interface reference.

holy crap. we always need setters and getters for upward references
because the container doesnt exist yet.

however, if we use VML\... everything comes into existence at the same
time.

aw crap, it means we cant have constructors. or at least, the
constructors wont be called.

that\'s kind of scary, really\...

how would a chronobase contain a chronobase? there must be an elegant
way to do this.

shit man i have no idea

how do we compile different modules together?

supposedly it\'s too much to have in ram, so we\'ll be writing to disk.
but what do we write to disk?

option 1:

do all the parsing steps together, then coalesce them all together. then
do all the typing steps together, then coalesce them all together. then
do all the codegen steps together, then coalesce them all together.

option 2:

do the parsing then typing then codegen for one module. keep the results
of each stage around for when other modules need them.

however, to do typing for one module, we\'ll undoubtedly need the type
information for other modules. if they\'re templates, then all hell
breaks loose. i think we need to do all the typing together basically.

that\'s going to be quite challenging. relying on paging would be
dangerous since we would need to be very careful that the important
stuff doesnt share a HD page with nonimportant stuff. since we\'re
manually managing it, we might as well just manage the writing to memory
ourselves.

to do all the typing, we must bring in the typing information of only
the direct dependencies. but it cant just be the direct dependencies,
since it could trigger a cascade of new functions in the indirect
dependencies too\... man, what a mess.

so, we have to be ready to do the typing for anything at anytime. we
need all typing information to be accessible.

answer: we do parsing separately in parallel, typing all together, then
code gen separately in parallel.

in chronovector, we can save space by having a frozen leaf node pointed
at by multiple things. this is perfect for immutables, since they don\'t
really have an identity.

however\... could some mutables have this behavior? for example, if we
have a mutable vector of unit, we might want to repeat null a bunch of
times.

null isn\'t really a unit, it\'s a lack of something.

but wait, chronobase is kind of a special world, where it does
interesting fancy things with mutables and immutables\... when
timetravel is involved, all bets are off.

this underscores the need to be able to bend the rules\... or is that
just a shortsighted unimaginative response to a difficult situation? if
we were to expand the rules to allow this kind of thing, what would
happen? could we introduce a special kind of shared global mutable
object? perhaps null is an immortal, and so owning and borrow and shared
mean nothing.

investigate diffing of the chronotrees\... would be pretty fast, but
probably not as fast as just looking at the mutations.

should do the hybrid one where we save mutations, so we dont have to
snapshot so often.

s\*.{&\_}

turns something into a borrow list

s\*.&m

gets a borrow list of all members named m

we dont need to optimize cli that hard cuz il2cpp will probably optimize
it. for non unity, doesnt matter that much since people using clr
probably dont need hardcore performance

how to enable downcasting of interfaces?

i dont see any reason to not have functions on the superinterface for
each of the subinterfaces. it\'s verbose but that\'s it.

why use vale instead of C# for unity?

\- get the speed of c++cli without using the nightmare that is c++.

\- and its modern, every other CLR language is absolute crap

when do you use c++ for unity?

all those same reasons will apply. except that more engineers are more
familiar with c++

is c++cli optimized at all?

itd be nice to have root pointers outside. really nice. but i worry
about the overhead involved\... how much is there per root? iirc
constant right?

when c# needs to reach into a vale object, it can use a \"control
object\", sort of a handle kind of thing. it is basically \*the\* weak
pointer for the object.

every CLR vale object will have in its header a pointer to the control
object, and the control object will have a pointer back. whenever we
give a vale object to C#, we\'re actually giving one of those control
objects. when the vale object dies, it will have the control object
point to null.

note, only things marked external can have control objects like this.

the control object will have all the same methods, but will return other
control objects.

it\'s probably going to be slower since there will be more cache misses
from going through these intermediates. is there any better way?

vale can have references to the outside though, easily.

also, to lock this weak pointer, we would give the vale core a delegate.
thats the only way to mimic RAII. but wait, what if they put that
pointer somewhere else? perhaps we should take the delegate but inside,
we increment before and decrement after the delegate call, using try
finally. so, it\'s a way to trigger a panic if vale frees it. however,
we will still use the weak handle for everything, inside the delegate.
we\'re just guaranteed it wont be freed.

can use this in jvm too.

in c++, can use something that\'s basically the borrow pointer under the
hood.

swift could do something similar, but using the object\'s native ref
counter thing.

make a simple version that is a thread loaded by dll\...

 and has agents for talking to presenters?

make it so a runtime sized array can be put on the stack. might need a
special llvm alloca thing.

or a stack-or-heap thing, see
[[https://stackoverflow.com/questions/27859822/is-it-possible-to-have-stack-allocated-arrays-with-the-size-determined-at-runtim]{.underline}](https://stackoverflow.com/questions/27859822/is-it-possible-to-have-stack-allocated-arrays-with-the-size-determined-at-runtim)

what if we have a \"flush\" function in superstructure which will check
consistency and then push/publish all the changes so far. it would then
resume with the rest of the execution.

could be useful to see the intermediate steps in an execution. the
execution could then just pause.

we might want to make an index for units, bucketed by level, then
bucketed by alive, then sorted by next move time. the tricky part will
be automatically updating it when any of those changes.

think about interop with dart/flutter, it will be very tempting to use v
and dart together. look up dart bytecode?

if we keep in the root a linkedhashmap of the most recently edited
things\... when we revert we can go backwards through that.

every time we modify something we\'d move it to the front of the list.

would make our reverts O(c) where c = changes since then.

unfortunate that this cant be interleaved with our incarnations\' hash
maps\...

the code that updates this can happen at the same time as the code that
updates the cache.

add a cache to the chronobase for extremely large data sets. a big
flatmap is much better for lookups than a persistent hash map.

make it so the user can specify the size, maybe even change it on the
fly.

maybe even make it configurable per type.

itd be nice if we could manually have them cached too. mark each unit
and item on the level as cached or not.

what if we had a way of saying \"this var will be moved out via return
so just initialize it in place\", perhaps an \'out\' keyword

all these virtual methods are screwing with the boundary between the
model and the game. how will we make it so we can swap in a non
chronobase?

maybe extensions could help\...

or maybe we have to use typeclasses for any virtual methods

or maybe we have to do enums like rust

or maybe we can generate it all\...

or maybe we can leave it to the user to define their handle classes, and
we would just generate the root and incarnations. then they could go
crazy with their methods.

the regular kind of handle is basically an incarnation as a class. a
chrono handle contains a root and id.

at the end of a revert for a given unit, swap out its incarnation for
the old one? or just change its version? probably the former.

we need to identify one struct as the top level document. these can be
tracked by chronobase. things inside one top level document cannot point
to things inside another top level document.

many different kinds of things can be top level documents, i suppose\...

using return values should be mandatory for at least pure functions.

First of all, panics are perfectly safe. None of this has to do with
safety guarantees.

\- Steve Klabnik

a smart pointer class can just have its dot syntax go to the pointee. if
we want to use things in the pointer we can use ufcs of that
smartpointer type.

myUnit.(ChronoRef.getVersion)(myUnit)

or

ChronoRef.getVersion(myUnit)

whereas myUnit.getVersion() would call the pointees method.

need indexes for chronobase\... but how? would they be implemented with
observers perhaps, so they can be attached to arbitrary forks?

should be built into schema and built into the generated code for speed

how do we initialize a giant contiguous intraconnected object?

we somehow have to grab memory from that object even before we
officially start using it\...

this should be doable with \'this\' syntax. we always know all the types
and memory details of inline things, so this is definitely doable.

we might have to make it syntactically clear that that\'s what\'s going
on\...

can we broadcast events from a superstructure? it\'d be nice, so we can
do spell effects and whatnot. perhaps they can be wrapped up in the
request observers? or perhaps they can point to their containing
request? if needed. maybe it wont be.

idea for superstructures\... perhaps we can mark any given request as
\"observable\" and then we know it will broadcast a beforeABC and
afterABC event. if that request calls another request to do something,
then we can broadcast beforeXYZ and afterXYZ within the beforeABC and
afterABC calls. mayhap we can even have a parameter that\'s a stack of
\"parent requests\", so we can know the context about how this thing is
changing.

it\'s unclear whether this will be needed\... we might find some sort of
zen that says we shouldnt/neednt do this.

rethink how we do readonly things in C#. it has no concept of const, and
no covariant return types. might need to make a parallel hierarchy of
things to handle constness\...

we cant expose the fields directly because C# doesnt know what to do
with ref counts and stuff. gotta be getters and setters. however those
work.

if something\'s exposed, itll have to have a boolean or something to say
it\'s dead, so that anyone who tries to read or write it will die.

a chronoref is basically a pool pointer with an id. we might have other
such things, for arenas and stuff. perhaps worth abstracting?

probably better to just keep the code completely separate and compiled
in a different mode. a dialect of v.

can we do a recursive immutable iterator? it can just give us the
primitives\...

but that doesnt work with interfaces. how do we say that none is less
than some?

perhaps we can go off definition order? would be gross though.

perhaps its fine in the case of sealed. what about open interfaces
though\...

struct Item {

  owningMarine: &weak Marine;

  fn destructor(this: Item) {

    let Item(owningMarine) = this;

    println(owningMarine.item);

  }

}

struct Marine {

  item: Item;

}

let m = Marine(myItem);

mut m.item = some new item;

we can\'t override and override because we can\'t provide
implementations inside traits\...

also, structs can\'t override anything alone, one has to be inside an
impl for a certain trait.

we don\'t actually want structs\' destructors to be virtual/override. we
do want interfaces\' destructors to be virtual though, of course.

so really, what we want is for every mutable to implement
IDestructible\...

so, every interface, we want to just hook up a little destructor call to
the underlying struct\'s destructor.

for now, we can split them into two things: a templated struct\'s
destructor, and then for interfaces we can do another whole thing.

so, every interface will have an idestructor method, whose default is to
call destructor.

later on, we can switch to rust\'s impl system. in that\... every
interface will extend IDestructible, and every impl will have a thing
forwarding it to destructor().

what does it even mean for a freestanding function to override a struct?
i think that means that this is the implementation for any matching
function in all interfaces.

what if we had deterministic allocation? or\... what if our default
allocator is deterministic?

actually, we don\'t necessarily need determinism, we need recordability.
so as long as we can record the addresses that malloc gives us, and then
recreate that later on, we\'re good.

actually, that might not work, because malloc shares one address space
for all threads, and no two threads can share address space at the same
time.

so, we need a per-thread allocator, which is either deterministic or
recordable.

we\'d still like to use pointers, for example in hash sets. it\'s
nondeterministic but it\'s fine if we can make it replayable. here, we
can make it replayable by intercepting the ptrtoint method. in the
original run, ptrtoint will just do a cast. if it\'s recording, it will
record what it\'s returning. in later runs, even if the allocators give
us different integers, we can still return the same integers we got from
the original run. maybe this doesn\'t even have to be a recording, it
can just be a map maintained somewhere.

sending rc things across realm boundaries is a terrible idea because we
have to copy all the rc in there. (its better than crashing)

this might be a good reason to only have atomic ones\... we\'ll have to
see how well it all optimizes

have a general \"cluster\" (clus?) sort of thing, that\'s enforced by
static lifetimes.

can we borrow something that lives inside a cluster? perhaps if we
somehow record the borrow counts of everything inside\... perhaps we can
have an \"open\" method that does that? and then when you close it, itll
do a runtime scan of the entire thing.

crap, #C:Coord is a template call!

perhaps we should require parentheses for template calls?

or maybe Coord should be reserved?

or maybe they can use parentheses just to disambiguate?

it\'s fine that we dont know the types of anonymous runes.

remember we really only needed the types so we could figure out .kind,
.ownership, etc. after all, one day we might have other things besides
coord with a .kind.

we can probably infer that a return value is a cluster. for example, a
\'map\' operation could do it\... as it assembles the new list, it will
note that nothing from the outside enters this new data, and therefore
be a new cluster.

a mutexes, superstructures, etc. are all clusters. when we lock a
cluster, we\'re guaranteed that it\'s self contained. that would be a
good time to do any compaction.

when interoperating with c#, java, C, any of them, you have to use a raw
pointer. raw basically means that you have nothing to do with its memory
management.

next challenge: how do we call methods on java/c# objects?

in C, extern just means it\'s provided from the outside.

but what about java/c#?

remember, an interface/fatptr/typeclass in the JVM is actually an
object. so if we\'re dealing with the outside, really, everything\'s an
interface.

how about for java, if we want to interact with objects, they have to be
treated like extern interfaces. in other words, the function would be an
override function of an extern interface. but how do we call a static
method?

seeing as V can be used with any language, it would be weird to have
annotations in V. imagine having:

\@Java(\"net.verdagon.thing.MyClass.moo\")

\@C(\"NVTMyMoo\")

\@CS(\"net.verdagon.thing.MyClass.moo\")

extern fn moo(a: Int)Void;

so perhaps we should instead have a mapping file passed in to the
compiler?

cant put strong refs on rust because of enablesharedfromthis

if we see a missing curly brace, then use indentation to guess where it
would have gone. at the very least, use indentation to figure out where
the functions begin and end.

so a mutex will give you access to the clique inside it. can we have a
threadsafe reference to another mutex\<Marine\>?

if we were to allow modifying the same state from multiple threads, how
would we do it?

chronobases perhaps\... but is there a simpler way?

can we have mutexes? we need to make sure that the mutexed data is a
clique. we can do this by counting the number of places outside that
have references to it. a couple ways:

1\. when we commit/unlock, scan the clique to make sure all references
are from inside.

2\. have a LockedHandle, a sort of smart pointer which points at a
counter int of how many LockedHandle things are pointing at something
inside the clique. accessing a member of a LockedHandle will make
another LockedHandle, unless it\'s an ext imm. when we commit/unlock,
check that the int is 0.

LockedHandle is almost like chronorefs in a way, it\'s trying to be a
smart pointer.

perhaps a cone/rustish approach: have another dimension in our
references: the lock lifetime. then we can do some static analysis to
make sure that nothing inside the lock can escape the lock.

or we can have something like Rust\'s Sync, that guarantees anything
inside it must also be Sync.

in CLR, we\'ll want to compile \<=32b things to structs. if we ever want
them to not be inline, they can be wrapped in a class to make it
referency.

how would we generalize the ! from rust? (@ in ours)

perhaps we can add a .toBool() to functions, which is automatically
called in the context of if-statements, in the context of @, and in the
context of !.

options return whether they\'re Some, Result will return whether theyre
Ok.

! will assert that .toBool() is true

@ will check if .toBool() is false, and if so, send it upward

if will check if .toBool() is true, and if so, take the \'then\' branch

i wonder if well ever wrap an imm object in a mut? probably. can wr do
some automatic stuff for that?

if so, we can have a None even for mut lists.

i just want to be able to say None() not None:Marine(). or maybe we can
use scala ish covariant params?

how do we get a default for a nullable value, like JSs \|\| ?

how about or? so yeah, like js. if left side is nullable, will return
left\|right.

what if and produced a ?\[left, right\] or maybe even a ?(left, right)

if let (x, y) = maybeX and maybeY

if let \[\[x, y\], z\] = maybeX and maybeY and maybeZ

btw, we should allow getting a thing from an optional with just
brackets.

if let \[x\] = maybeX { \... }

it can only be done on sealed traits where its unambiguous though. or
perhaps we can define a toSeq on interfaces?

rust has a way to destructure vectors, like:

let \[x, \...y, z\] = myVector;

maybe we should have something like that too.

\* is map

/ is filter

\| is flatmap

\*. is map for member

/. is filter by member

\|. is flatmap by member

myList/.x filters by their x

myList\|.x flatmaps by their x

we need a flatmap operator. map is .. which is nice, because it matches
\...

but what if we got away from both of those, and had something else?

or, what if \| was flatmap?

we really, really need a flatmap operator.

blog post about flatmap

talk about how in JS, x \|\| y \|\| z is basically a flatmap if those
are all option types

in swift, thing?.method()?.method() is a flatmap

talk about how unix is basically all flatmaps

\% could mean throw an error upward, or perhaps @. @ would be consistent
with php, funny enough.

would take the address of a nullable give a\... nullable borrow ref? we
should probably stick to Some and None

instead of:

let

let var

mut

we could have:

val

var

mut

if one of those is discovered anywhere, we\'re dealing with a pattern.

it\'s a little riskier parsing wise though\...

actually not, there\'s guaranteed to be one either at the start, or
after the first parentheses.

(mut x, val y) = doThings();

var y = doStuff();

there might be destructuring or more complicated stuff in there.

\[val a, val b\] = doStuff();

but that\'s tedious as hell to put val in front of so many things.
imagine if we had to do that in scala, ick.

what if, for templatas, we can use specifying parameters to specify
packs?

Callable:(params=(Int, Bool), ret=Bool)

it would be cool to be able to do that in regular V code too\...

in fact, there\'s no rule that says we have to flatten ((a, b), c) into
(a, b, c). we just have to be able to flatten ((a), b) into (a, b). and
we can\'t access a pack directly.

perhaps, if something\'s declared as a pack, then it can receive either
a single value or a pack, but it cannot be used directly, it must be
exploded.

So, callable would become\...

ICallable:((Int, Bool), Str)

BEAUTIFUL.

interface mut ICallable:(#Params: Pack, #Returns: Pack) {

  fn call(params: \...#Params\...)(\...#Returns\...);

}

difference between a lambda and something callable:

#K: Callable\[#M, \[#A, #B, #C\], \[#R\]\]

#K = fn(#A, #B, #C)#R

is there a way we can isolate away from the rules and stuff the fact
that immutable things arent owned and borrowed?

theyre basically readonly owned things, that happen to be optimized for
copying.

can we just\... think in those terms?

a \"raw\" struct is a special kind of struct that is interoperable with
c and perhaps rust. one can only have owning and unsafe pointers to it.
PERHAPS in the future if we figure out how, we can lend out
borrow-checked borrow refs.

since they\'re compatible with c, they follow c layouts, and they don\'t
have headers for the borrow count, which is why theyre so irksome. i
imagine it will be common to wrap them in a regular struct.

theyre allocated with malloc and freed with free. moving nor copying is
their default, one has to specifically say mov to move it.

Have assertions and constraints as part of any variable.

parser isn\'t caught up to the templates/parameters/patterns spec. add
tests and finish it!

rules(

#C: ref\[#O: ownership = borrow \| own, #K\]

#C: ref\[#O = borrow \| own, #K\]

#C: ref\[borrow \| own, #K\]

#C: ref\[#O, #K\]

implements(#C, #I)

#O: ownership = borrow \| own

#O: ownership

#O = borrow \| own

)

(capture): (type)(members) = (possible values)

can just require that they say it beforehand. a: \[Int, Bool\]\[a, b\]

we dont allow destructuring without something in front.

sweet. make everything use square braces again. huzzah!

and even use it for rules! yay for syntax!

a halfway between go\'s interfaces, javas type erasure, and c++
stamping, is there a way to combine similar functions into a itable
approach

Each value in Rust has a variable that's called its owner.

There can only be one owner at a time.

When the owner goes out of scope, the value will be dropped.

&#T matches borrows only, \^#T matches owning only, #T matches anything.

Putting \^ in front of a borrow reference like \^&Marine will make it
owning, putting a & in front of an owning reference like &\^Marine will
make it borrow. that way, if i capture a #T as own, i can do &#T to it.

can do rustish borrows of the owning reference, to guarantee it wont
die? can we track that?

cant someone else reach in and change the owning reference to something
else

perhaps we can do runtime deep locking with the rustish borrows

rust will prevent you from getting multiple things from an array right?
can we do the same?

[[https://www.reddit.com/r/rust/comments/7dep46/multiple_references_to_a\_vectors_elements/#thing_t1_dpx9wj6]{.underline}](https://www.reddit.com/r/rust/comments/7dep46/multiple_references_to_a_vectors_elements/#thing_t1_dpx9wj6)

at runtime, when we get a reference, we make sure its still in there;
theres no references to it anywhere. in V thatd mean nobody is actively
modifying it right now. if we have weak pointers that we turn into
strong borrows, and only allow one mutable strong mutable borrow, or
multiple strong freeze borrows\... thats basically what rust is doing. i
kind of like the previous way though, where we dont need weak pointers.

\"The program is not in an expected state, and its execution has become
unpredictable. Immediately stopping its execution is by all means the
best thing to do\"

can do three kinds of compiler plugins: values, functions, and
templatas.

will we also want something like rusts derive that can modify a struct
def?

use square braces instead of curly braces for destructuring. when a
struct comes in, call toSeq on it and then match that.

also, fun fact, youll never see parentheses in a pattern. really we dont
need parentheses to represent packs. parens are really only ever used to
denote unary group and also function/template calls. we dont even need
them for patterns or returns. so, what can we use parens for?

a: #T Marine{hp, str}

a: Marine{hp, str}

a: #T{hp, str}

a: #T \[Int, Str\]{hp, str}

a: \[Int, Str\]{hp, str}

a: #T{hp, str}

if we want any more rule than \"coord\" for the #T, it has to go in the
template rules block.

it doesnt make much sense to have a tame and a templex at the same time.
if we have a tame then it must be part of a pattern.

i want to be able to capture a type in a let statement.

let template(A: coord) a: A = 6;

have a tenplated struct banner curry, with a name in it. when someone
tries to put it in a referend or coord, thats when we require that only
one struct1 in the env matches its pattern and then try to stamp it.

ResultOr\<T\> should have some branch predicting so we\'re fast on the
good path

Using StructRef2 for both its terry and its manifested form is a
terrible idea

InferTemplar.matchReferend2AgainstReferendFilterP1 has a part where it
extracts a StructRef2 from another StructRef2 and it\'s horrible

itd be really nice to be able to just feed { 5, 9 } to something instead
of MyStruct(5, 9)

StructRef2 and InterfaceRef2 should become StructIdT and InterfaceIdT

Reference2 should become CoordT\[T \<: TypeT\]

Referend2 should become TypeT

Addressible2 should for now become AddressT

StructRef3 and InterfaceRef3 should become StructIdH and InterfaceIdH

struct ID and interface ID should become StructIdNumH and
InterfaceIdNumH

Reference3 should become CoordH

Referend3 should become TypeH

fn moo

:(overrideable T: Reference(\_, type))

(obj: T)

{

  doThings:T(obj);

}

two options for interacting with C code:

\- agents, which can check that pointers are still consistent

\- unsafe structs, which contain only unsafe pointers

add test for:

  // Takes a LocalEnvironment because we might be inside a:

  // struct:T Thing:T {

  // t: T;

  // }

  // which means we need some way to know what T is.

clasp reference, can be connected to another clasp reference.

struct Marine {

  partner: ? &clasp Marine:

}

mut m1.partner = m2.partner;

hmmm not good

clasp(m1.partner, m2.partner);

maybe

the answer to the inline types in constructors and destructors is: the
other let syntax!

let this.blork = Blork(1, 2);

let this.moo = Moo(4, 5, &blork);

those can be inline, easy.

perhaps we can do something where if an Rc is sent to another thread, it
triggers a copy? this could even be an opt-in compiler flag. (default is
disabled, in which case wingman will halt if it finds an Rc sent across
a thread, even if it\'s only shared internally in the cluster)

main shouldnt be able to return a pack, because there\'s no way to
represent a pack in VON. or maybe it should, and we can think of it like
main is just producing a list of VONs?

perhaps we can have another entry in the vtable for a pointer to the
function that produces a VON?

once we have options and lists stable enough to go in the commonenv,
then we can use them to define VON. then we can have our tests able to
return VON.

if we need to move from one position in a vector to another position, we
should have a move function. it\'s basically a remove operation and an
add operation. the reason we want a function for it is because that
first \'remove\' operation invalidates all existing iterators, including
the one that would be given to \'add\'.

when Vivem looks for a destructor, it really should look for it using
the templar types rather than hammer types. that way we wont confuse the
destructors for packs and user structs.

if we do have hammer flatten viee objects, it would make global const
instances of itables. but, the functions in them would take void\* as
their first argument\... how, in llvm, will we cast a function that
takes a Marine to a function that takes a void\*?

is the answer covariants?

is the void\* perhaps an Anything or a Nothing? i think we can cast a
Marine-receiving function into a function that receives a subclass of
Marine. a Nothing fits that, actually.

so i guess that means that the functions in the itable struct accept
Nothing\...

does that mean that the second half of the fat pointer is a Nothing?

mutate should be swap. In other words, the previous value\'s destructor
cannot be called before the new value is in place, because of this case:

struct Item {

  owningMarine: ?&Marine;

  fn destructor(this: Item) {

    let m = this.owningMarine!;

    let \_ = explode(this);

    println(m.item);

  }

}

struct Marine {

  item: Item;

}

let m = Marine(myItem);

mut m.item = some new item;

That println accesses part of a struct that is in the middle of
deinitializing, which is bad.

So, the destructor has to be called after the new value is in place.

Currently, Rust provides no means of tail-call optimization (TCO). This
is quite intentional, as I was helpfully taught by the Rust community a
year or two ago. Automatic TCO is brittle and has the potential to be
broken by moving code around. This would lead to silently broken
performance expectations and potential runtime failures. I buy the
argument, and look forward to something like the become keyword being
added for explicit TCO.

if we\'re to mutate something, we need to have a pointer to the thing,
the thing that contains a (null\|Marine)

fn main() {

  let m = Marine();

  doStuff({

    blork(m);

    mut m = 5;

  });

}

m will have to be conceptually a Box\<(null\|Marine)\>

it\'s a box so we can mutate it from inside

it\'s a nullable so we can move it away

the (null\|Marine) can be represented on JVM with type classes

in llvm, it should be optimized down to a \[int tag, void\*\]

so, hammer should have no notion of a move, but should instead use a
swapmutate, and give it a union with possibility 0 (null).

AddressibleLocalMove3 should be changed to an AddressibleLocalSwap, the
templar will put in a null.

hammer still outputs things in terms of addressibles\... we gotta fix
that to output only references.

the reason we have a Void2 on the inside is because we need something to
return from destructor calls. if they returned something we had to deref
or dealloc then we\'d have an infinite loop.

the reason we have Destructure2 put into locals is because we have no
way to return a set of registers. the reason we cant return a set of
registers is because then our AST wouldnt be an AST, it would be an\...
ASGraph. and that\'s terrifying.

in structmember2, an address member\'s variability refers to the
variability of the pointed at variable

let Marine(hp, Item(power)) = Marine(9, Item(5));

let \_\_pattern_1 = Marine(9, Item(5));

let Marine(\_\_pattern_3, \_\_pattern_2) = unlet \_\_pattern_1;

let hp = unlet \_\_pattern_3;

let Item(\_\_pattern_5) = unlet \_\_pattern_2;

let power = unlet \_\_pattern_5;

when we lend the input lookup expr to a sub pattern (or more than one!)
then it\'s on us to destroy it. this is because when there are multiple
sub patterns, we dont know which one of them should destroy it (maybe
the last one? seems easier this way tho)

ordinary/templated

plain/templated?

simple/pattern

light/closured

mundane/boxed

language/compiler (compiler is a compiler plugin, language is defined by
the language)

reference vs address vs noid expr. noid means it produces nothing.

chronobase superstructures could really easily do compaction, since
everyone outside is just holding onto an ID.

handle this case:

let x = \[1, 2, 3\]

\... (stuff that uses x)

\... (stuff that doesnt use x)

x = \[\]

we should make that into:

let x = \[1, 2, 3\]

\... (stuff that uses x)

unlet x;

\... (stuff that doesnt use x)

let x = \[\]

in other words, release that memory so RC can reclaim it.

write blog post about how cache being the bottleneck changed the
language design, like vptr vs fat ptr

lets do splode and reverse, and make that the default destructor. lets
always name it destructor, and give a default template. thats the
easiest and most conceptually pure.

later, maybe add unlet for locals for better control of order, at the
same time as order detection.

tried doing a compile time check to guarantee that deterministic
functions dont do anything nondtereministically. it was looking like a
medium sized can of worms, but the biggest problem waa that theres
plenty of code thats nondeterministic in prod but deterministic in tests

write a blog post about it

perhaps the pipe operator can be like map but assumes we\'re moving?

instead of myStuff..method()

we\'d do myStuff \| method()

no i like the first one more, it really sends the message of the hidden
this arg

thing.(v.moo)(4)

might be interpreted as indexing.

lets instead use mikes \' operator or an .at method.

if we have a single threaded superstructure, and we make a new version,
could we keep adding things to the same hash map? and when we land on
the entry from the previous version, just swap places and move it down a
bit. when we reaxh the previous previous version, move it out into a new
vector somewhere\...

alternate idea, we have a massive hash map which can store like 10
versions. when were adding a new node, once we find an empty OR a node
from 10 versions ago we use that space. once we reach capacity we
assemble the persistent vectors then\...

if every immutable comes with a unique ID instead of a pointer, each
thread can have its own local hash map of ref counts.

then we just need some way to know when it has none on any thread. we
can send a message to a central coordinator who can check if any other
thread has any references to it and if not deallocate it.

theres a chance one thread will have a negative ref count and another
will have a positive, if one thread continuously sticks things into
structs and sends it to another thread\...

internal immutables would be nice because we can represent them not as
pointers but instead as indices into a giant sky vector.

come to think of it we can do the same thing for chronobases, since
theyre contained. thats good because we want to send those over threads.
but it does mean we can ONLY send the entire chronobase, not specific
little items.

what if we have \"adaptive\" classes which will be immutable or mutable
depending on what they contain? we already have that with pack and
tuple.

but we need it with option.

struct:T Option:T adaptive {

   value: T;

}

defining those would be a nightmare though. perhaps instead have:

struct:(T: imm) Option:T imm {

   \...

}

struct(T: mut) Option:T mut {

   \...

}

it would make constructors:

fn:(T: mut) Option(val: T): Option:T { \... }

fn:(T: imm) Option(val: T): Option:T { \... }

and everything would just work out.

if we have a small 32 bit immutable that points to another large
immutable, doing an inline means we still have to do an atomic
increment.

inlining was one of the best defenses we had against these atomics\...
this is probably a reason to have a \"migratory\" keyword or something
to say that this value could escape. then we can just have tiny
increments and decrements.

migratory things can only contain migratory things. but nonmigratory
things can contain migratory things.

if anyone bitches about it, tell them this is basically Rc vs Arc, but
with better rules (enforces Arc contains only Arc, and auto conversion
from Rc and inl to Arc)

todo:

\- test returning a pack from a function and flattening it when handing
it to another function

\- test that we evaluate the stuff on the right side of the mutate\'s =
before the stuff on the left side

\- virtual destructors. we should require a destructor for every struct
and interface, including packs and stuff. arrays too. the compiler will
be smart enough to inline the packs, structs. arrays too. so, we can be
sure that if we have a structref, there\'s a destructor in temputs for
it. we\'ll want to make making arrays go through temputs too for that
reason. theres a possibility of defining the same destructor (for
example for \[int, int, int\]) in two different libraries but thats
fine, theyre guaranteed to be identical.

we need a \"swap\" instruction

struct Marine {

  mut item: ?Item;

}

let m = Marine(none);

(muts m.item =

closure compiler can do:

if (myobject.something) {

  println(myobject.something); // knowing it\'s not null

}

the reason we can do that too is because mutable objects are restricted
to only one thread.

\...we really should change it to \^.

note somewhere that it makes no sense to move from an array. one must
splice/shift/pop it out. actually, that only works for a vector. for
arrays, one must explode it.

struct Marine { item: Item; }

to explode a struct and get only that member, you must \^. it. so,
marine\^.item will move only name out.

so, when we do marine.name, we\'re borrowing marine, and since marine
owns name, we\'re also borrowing name. question is: should we
automatically infer this, or require them to put an & in there?

print(marine.&item);

can only non-explode move out of a pack. the reason for this is because
of:

let (x, y) = (Marine(6), Marine(8));

we need x and y to \*own\* the things, which means we need to move out
of the temporary pack.

this is actually fine, because the syntax rules of the language ensure
that you can never do anything with a pack except explode it.

now, what about the case of:

let \[x, y\] = \[Marine(6), Marine(8)\];

in this case, we need an \"fn explode(sequence here): (pack here)\"
function to turn it into a pack first.

pack\'s existence is pretty convenient here.

since we\'re moving out of a struct, that means we have to account for
that in the VM and probably also in java, we need to null out the part
of the pack.

we should probably do a lot of tests that feed that kind of temputs into
the compiler and VM and see if all works as expected.

blog about challenges of jvm. namely, how register order wasnt a
problem. also, how we had to box things in a way thats still inlined by
llvm

spell to coalesce all the ambient darkness into a dark bird. can send it
to attack an enemy.

if a creature feeds off darkness, this is a good way to remove the
ambient darkness.

if theres a darkfeeding creature and a regular creature, the darkbird
can drprive former and attack latter.

can use multimethods to downcast!

superinterface IBlah {

  interface IMarine { hp: Int; }

  interface IItem { name: Str; }

  fn doSomething(m: IMarine, i: IItem);

}

superstructure Blah {

  import IBlah;

  struct Marine {

    implements IMarine;

    override hp: Int;

  }

  struct Item {

    implements IItem;

    override name: Str;

  }

  override fn doSomething(override m: Marine, override i: Item) { \... }

}

or

impl Blah for IBlah {

  impl Marine for IMarine {

    override hp(self): Int = self.hp;

  }

  impl Item for IItem {

    override name(self): Str = self.name;

  }

  override fn doSomething same as above

}

if we just add some sugar, this becomes really easy to reason about.

we might still have to check the given borrow ref arg belongs to this
superstructure\... or mandate only calling functions can modify an ss
perhaps\... that would mean only ids and values can be passed in. hm.

alternative: will take an interface arg like regular, then have a
special thing check to make sure its from inside this ss.

what happens if we add a transient pointing to obj from other ss? we
should probably recursively check for either transient or in this ss.

superstructure Blah interface IBlah {

  struct Marine interface IMarine {

    struct interface hp: Int;

  }

  struct Item interface IItem {

    struct interface name: Str;

  }

  struct interface doSomething{

      m: interface IMarine struct Marine,

      i: interface IItem struct Item) { \... }

}

man even that is tedious\...

doing a java-interface-style proxy on a superinterface is exactly the
kind of thing we need to do the call-\>message translation. with that,
the magic of superstructures goes away

should have unsafe blocks and void pointers for the same reason rust
does

dots:

\^. to move, can explode too if applicable

#\. to lend a const

.! to expect

.? to map optional field access

.. to map to get field, or call method on each

\^ means owning, & means borrow. in template perhaps we can say T:
\^something

if we just instantiate a block outside of an if/while/match, then its
variable uses should count as maybes. when we hand a block to a user
function, we have no idea if it will be called.

we should also consider { \... }(); \'s variable uses to always happen.

to do this, scout might have to do unscrambling. probably for the best.
will need an infix import up top. perhaps templar can still receive
scrambles that scout couldnt figure out, and templar could make better
errors with suggestions

Make templar aware of whether variables have been mutated or moved down
in the closures \*and possibly later read\*. track whether its certain
that it was moved (see if we pass up through any whiles or ifs or into
any non-inline functions) that way we can make them into addressibles,
and anyone who does a possible-move from an addressible.

wait\... once we figure out the type in the templar, we can combine it
with parser knowledge of whether it\'s given away?

Also make templar think of variables in terms of indices, like jvm.

rename inc/decrementReferenceRefCount to receiveReference and
unreceiveReference

test moving an element out of a struct and an array

every struct and array should have a destructor. primitive types need
none.

(\_ : Str) could be used in the body to check magic param\'s type

should make mut return the old value. that way it acts as a swap. or,
have a swap operator? probly best just have mut.

do we want to have an intermediate state where the containing object
isnt consistent? or do we want to have the object able to exist outside
its containing object?

we should make an \"explode\" instruction, to pull things from an object
into registers\...

we should be able to have private destructors. that means you have to
move the object into one of its member functions, and it can destroy
things. this lets us do destructors with parameters, kind of.

what if collections had a mutable \"each\"

\- function

  - lambda (has body, no name, pattern signature)

  - abstract (no body, has name, no pattern)

  - extern (no body, has name, no pattern)

  - ordinary (no body, has name, pattern signature)

         body pattern name templated closing extern

ordinary y y y y n y

lambda y y n y y n

abstract n n y y n n

named

closing

canbetemplated

pattern

body

if virtual and no body, abstract not extern

if virtual and body, ordinary not extern

if non virtual and no body, extern

if non virtual and body, ordinary

Function0: a bunch of maybes

OrdinaryFunction0:

Function0

FunctionWithImpl0

FunctionWithImplNameAndPattern

BodiedFunction

BodylessFunction

string is a safe subclass of option\[string\]

so FunctionWithImpl0 should be a subclass of Function0

FunctionWithEverything should be a subclass of lots of other things

IOwned:OwnerType

has an owner pointer and a vtable index inside it

anything implementing this cannot be cast to something else that
doesn\'t implement IOwned:OwnerType

when we move an IOwned:OwnerType, we set those two things. when we first
construct it, or when it\'s moved to be owned by the stack, theyre set
to null.

maybe it also has an onMoved.

to mixin a class means to implement its interface, contain its struct,
and have forwarders. it\'s a way of doing inheritance.

IOwned:OwnerType would be a class we have to mixin.

huh, now we have multiple inheritance. and the diamond problem. crap\...

IOwning:OwnedType

the owner would be an IOwning:(? super OwnedType)

IOwning:OwnedType would have:

\- int setOwned(OwnedType thing) which returns the vtable index

if the struct has different fields, then itll have different vtable
indices for those

this is super tricky\...

also, this cant be used for immutable things. the setter is mutation, no
matter how we think about it. but if we can rephrase it as a getter,
maybe?

but, we only need it for controllers, so we dont care much

structs and interfaces

structs can only have virtual methods if there\'s an interface above
them.

has virtual methods -\> has vptr.

has virtual methods -\> implements an interface

implements an interface -\> has a vptr.

if something has virtual methods, or implements an interface, it has a
vptr.

if something has no virtual methods and implements no interfaces, it
needs no vptr.

if something implements no interfaces, then no dynamic dispatch is
needed for it.

we can say that a struct becomes a class whenever someone makes it
implement an interface.

that gets rather scary though. someone from afar can make something from
a struct into a class. to prevent that, we\'ll need either:

\- struct vs class

\- a keyword to lock something into struct-ness.

i like the keyword to lock something into struct-ness. then this keyword
can be required to serialize it.

maybe serializable?

soooo\... if something\'s both a struct and an interface, then what
happens when we say its name? probably should get the struct. yeah,
definitely.

so, we can think about this entire thing as if every struct can have its
own personal interface.

kinds of template params:

\"type\" is the underlying type, like MyStruct.

\"coord\" (temporary name) has the ownership and mutability, like
#!MyStruct or &MyStruct

\"variable\" has all sorts of annotations and stuff

weakness in the c++ approach: what happens if they give us a model
object to consume,

but they keep a nonconst pointer to it? they can modify the internals at
will!

first 4 bytes: type id. root, unit, terrain, game, etc.

next 4 bytes: mutation kind id. add = 0, remove = 1, the class itself
can define the rest.

rootmutation

can move from a list\|nullable to a list\|nullable.

units list for example. if we want to kill a unit wherever it is, then
we want to issue a delete unit mutation.

if we just want it moved to another list if it\'s still alive, then we
want to issue a move mutation.

so, lets have both.

can move from a list\|nullable to a list\|nullable. this is because the
unit might be killed at any time.

can also delete them.

can issue a delete mutation to any unit that is inside a list or a
nullable.

if in a nonlist&nonnullable to a nonlist&nonnullable, can\'t delete,
can\'t move. can swap.

so, can only delete something that\'s inside a list or nullable.

addvalue - the server has a new large immutable value.

removevalue - the server has just lost its last reference to it.

game gameid setunit (immediate) (unit contents)

removeunit

updateunit (many kinds of this)

when specifying a value, if it\'s a small immutable, then its just right
there.

if its a large immutable, can either specify it there with an id, or can
have a recall by id.

immutable things have a uint32_t ID.

if the owner has a raw pointer to our basic struct, then we need to only
set that pointer. if the owner has a fat pointer to us, then we need to
update that fat pointer to point to our stuff. and we dont know what
view of us it has.

we down here know what type we are.

up there, they know what type they want.

we would need some sort of\... NxM sort of thing.

we basically have to do hash tables of some sort here. either
proactively when we set the thing above us, or reactively later when we
call the function. so far, the answer has been reactively later. we
could do proactively\... if we know the thing above us is a fat pointer,
we can look above us into it, get the interface id, and find the fat
pointer that we\'re supposed to be.

but not every pointer is a fat pointer! it could be a reference to our
raw type.

the function above us does know about this. so, it would have to make
its own fat pointer. it would have to receive the thin pointer and then
update its fat pointer. that could work.

then we can just have one way to invokeinterface, the rust way. all the
work falls to the chronobase to update its fat pointers when necessary,
which is probably a good thing\...

what if we had a special annotation on fields that said \"thin\" which
meant that when you read from them, you have to also construct a fat
pointer from them. so, it\'s kind of done lazily. this annotation would
be on by default for chronobase, but in the end, ML could configure it.

i like this approach, because it uses fat pointers everywhere if the
user wants it, and they can opt in to more expensive calling if they
want to save some memory.

whats the default mode for vale? worse memory for better speed, or worse
speed for better memory? i\'d assume worse memory for better speed,
which means the fat pointer approach.

wait, but the chronobase needs to look up these itable pointers on every
modification, which could be quite expensive. so it depends, are we more
often making new models, or calling into their functions?

im not sure. depends how often we take snapshots. you know, that factors
into so many things\...

let l1_oldOwner = baseA.owner;

if (l1_oldOwner) {

  removeReference(l1_oldOwner, &baseA + \"-3\");

}

baseA.owner = player1;

if (player1) {

  addReference(player1, &baseA + \"-3\");

}

let l2_oldOwner = baseA.owner;

if (l2_oldOwner) {

  removeReference(l2_oldOwner, &baseA + \"-3\");

}

baseA.owner = player2;

if (player1) {

  addReference(player1, &baseA + \"-3\");

}

let l3_oldOwner = baseB.owner;

if (l3_oldOwner) {

  removeReference(l3_oldOwner, &baseB + \"-3\");

}

baseB.owner = player1;

if (player1) {

  addReference(player1, &baseB + \"-3\");

}

let l4_oldOwner = baseB.owner;

if (l4_oldOwner) {

  removeReference(l4_oldOwner, &baseB + \"-3\");

}

baseB.owner = player2;

if (player1) {

  addReference(player1, &baseB + \"-3\");

}

baseA.owner = player1;

baseA.owner = player2;

baseB.owner = player1;

baseB.owner = player2;

removeReferenceIfNonNull(baseA.owner, &baseA + \"-3\");

baseA.owner = player1;

addReferenceIfNonNull(player1, &baseA + \"-3\");

removeReferenceIfNonNull(baseA.owner, &baseA + \"-3\");

baseA.owner = player2;

addReferenceIfNonNull(player2, &baseA + \"-3\");

removeReferenceIfNonNull(baseB.owner, &baseB + \"-3\");

baseB.owner = player1;

addReferenceIfNonNull(player1, &baseB + \"-3\");

removeReferenceIfNonNull(baseB.owner, &baseB + \"-3\");

baseB.owner = player2;

addReferenceIfNonNull(player2, &baseB + \"-3\");

let l1_oldOwner = baseA.owner;

if (l1_oldOwner) {

  removeReference(l1_oldOwner, &baseA + \"-3\");

}

baseA.owner = player1;

if (player1) {

  addReference(player1, &baseA + \"-3\");

}

let l2_oldOwner = baseA.owner;

if (l2_oldOwner) {

  removeReference(l2_oldOwner, &baseA + \"-3\");

}

baseA.owner = player2;

if (player1) {

  addReference(player1, &baseA + \"-3\");

}

let l3_oldOwner = baseB.owner;

if (l3_oldOwner) {

  removeReference(l3_oldOwner, &baseB + \"-3\");

}

baseB.owner = player1;

if (player1) {

  addReference(player1, &baseB + \"-3\");

}

let l4_oldOwner = baseB.owner;

if (l4_oldOwner) {

  removeReference(l4_oldOwner, &baseB + \"-3\");

}

baseB.owner = player2;

if (player1) {

  addReference(player1, &baseB + \"-3\");

}

let base

let l1_oldOwner = baseA.owner;

if (l1_oldOwner) {

  let l1_id = [[l1_oldOwner.id]{.underline}](http://l1_oldowner.id)

  updateOrAdd(\[\"references\", l1_id\], {\[&baseA + \"-3\"\]: delete})

}

baseA.owner = player1;

if (player1) {

  addReference(player1, &baseA + \"-3\");

}

let l2_oldOwner = baseA.owner;

if (l2_oldOwner) {

  removeReference(l2_oldOwner, &baseA + \"-3\");

}

baseA.owner = player2;

if (player1) {

  addReference(player1, &baseA + \"-3\");

}

let l3_oldOwner = baseB.owner;

if (l3_oldOwner) {

  removeReference(l3_oldOwner, &baseB + \"-3\");

}

baseB.owner = player1;

if (player1) {

  addReference(player1, &baseB + \"-3\");

}

let l4_oldOwner = baseB.owner;

if (l4_oldOwner) {

  removeReference(l4_oldOwner, &baseB + \"-3\");

}

baseB.owner = player2;

if (player1) {

  addReference(player1, &baseB + \"-3\");

}

interface MyOption:T { }

struct MySome:T {

  implements MyOption:T;

  value: T;

}

struct MyNone:T {

  implements MyOption:T;

}

struct MyList:T {

  value: T;

  next: MyOption:MyList:T;

  fn forEach:F(this, func: F) {

    match(

      this.next,

      {:MyNone:MyList:T},

      {:MySome:MyList:T(nextList) nextList.forEach(func);})

  }

}

fn main()Int {

  let list = MyList:Int(10, MySome:MyList:Int(MyList:Int(20,
MySome:MyList:Int(MyList:Int(30, MyNone:MyList:Int())))));

  forEach(list, print);

  0

}

how do we have references to snapshots? it has a rather unique need: we
cant modify through it, and we cant get mutable references from it
dereferencing fields.

some context:

currently, const refs dereferencing owning nonconst fields will yield
const refs, and const refs dereferencing borrow nonconst fields will
yield nonconst refs.

that\'s a problem for superstructures, who want them to be const refs
for this purpose.

1\. do that by default, but have an annotation for superstructures that
make it so a const ref dereferencing borrow nonconst fields will yield
\*const\* refs.

2\. the reverse: by default, const ref dereferencing borrow nonconst
fields will yield const refs, but have an annotation that makes them
nonconst.

1 seems to make sense, but 2 sounds more secure. less people
accidentally giving mutable access to things they didnt want to. so,
lets get rid of 1. actually, both of them wont work because if we have a
feature like this for general use, then we cant automatically use it in
superstructures because what if they use it themselves in a
sueprstructure\'s struct definition? so, both 1 and 2 cant be used.

3\. a variant of the structs that only has const references in its
fields.

seems quite gross. lets save this as a last resort.

4\. an imm reference that, when dereferencing a field, is still imm.

this option rather weird in general, because one imm reference can only
beget imm references. this makes a lot of sense in superstructures
because they cant reference anything outside themselves. but\... getting
an imm reference to something is putting a massive restriction on it.
getting an incredibly powerful imm restriction from a least-privileged
borrow

sounds rather backwards. if anything, only the owner should be able to
lock something.

5\. an imm reference for only superstructures. a \"snapshot ref\".

another last resort option. i really don\'t like adding more
superstructure-specific features.

6\. make it so there\'s no such thing as a snapshot, instead only have
forks.

7\. a const reference that, when dereferencing a field (even borrows),
is still const. superconst, basically.

8\. have a user-defined & operator, that comes with a user-defined dot
operator. like:

fn .:(Struct, Field)(snapRef: SnapshotRef:Struct) {

   ret SnapshotRef:Result(snapRef.root, &snapRef.underlying.(Field));

}

We should implement both 2 and 8.

when we dprintln a snapshot, we get a human \*readable\* representation,
not necessarily valid vale.

more precisely, we strip off namespaces, we can strip off template
arguments, superstructures can put \# in there,

and we can use console coloring.

if we wanted valid vale, we could call, uh, mySS.raw().vserialize()

start here, we need some borrows in there. bring back the astronaut.

what if immutability didnt imply giving up ownership?

what if we just said that it didnt really factor into when its cleaned
up\...

and in fact, didnt factor into anything. hmmmm not sure\...

i like the idea of having an &:imm. if only we could abstract over those
and actual values.

if \# meant immutable, then we could do this somewhat easily.

you know, superstructure structs are the only things that can be both
immutable and not.

if we somehow conceptualize a live superstructure as a wrapper around an
immutable one,

like how chronotree/base are, then converting from a mutable thing to an
immutable one

is easy.

but, converting to a snapshot is not easy for linear, it\'s O(n).

we run into this so often\... the need to operate on mutable and
immutable things

with the same code. it\'s why &imm is so tempting.

what if\... we lazily snapshot things in linear? whenever we dereference
a borrow,

we apply mutations backward in time til we get what we want?

what if &lock means something different depending on linear vs
chronobase/tree?

&lock on linear locks the entire thing, base/tree also\...

wait, that\... huh. that makes some sense.

&lock will guarantee that nothing in the entire superstructure changes.

thats different than the existing &lock which only locked descendants.

what if, what if, we had some sort of temporary snapshot, only at head,

which was immutable? we could pass that to functions. it locks things.

it wouldnt even cost anything.

in other words, if you want a function to operate on both the live and

the snapshot, then freeze the head. this produces something that looks

like a snapshot.

so, this doesnt involve &lock, it uses Snapshot:. thats fine.

also, related, fork() is the same cost as snapshot() on linear.

\... same with the others. perhaps we should just kill snapshot?

actually its probably fine to require O(n) before using a function.

if theyre using a linear chronobase they probably arent doing much

with snapshots, and so probably wont.. hmmm\...

no, linear is a legit strategy. cant think that.

whats the goal here again?

sort by begin version

go forward until begin is greater than desired version

sort by end version

go backward until end is less than desired version

merge the two results together.

copying vector

mutating vector - good for

copying vector -

indervenchers

as long as shared things are immutable, they can point at live things.
because\...

imagine the ownership tree up above.

every new shared immutable can be placed below and point at the things
above.

could have a \"using infix v.math.vectors.dot;\"

that way, [[vmath.dot]{.underline}](http://vmath.dot) will parse as an
infix operator. boom, dont need to know the contents of other files.
massive speed up.

there can be an implicit \"using infix v.operators.+;\"

holy crap, CoercedTemplateArg2 is a use of annotations, of adding a
custom pointer dimension

a view, if borrowing, contains an owning reference

a view:

\- can never be borrowed

\- can be moved

\- it can be copied.

so is therefore inlined.

a view of a borrow pointer is like the above

a view of an owning pointer is like the above

a view of an immutable object is immutable

welp, i guess itables are gonna get implemented by templar or
something\... actually, carpenter.

we\'ll also need to implement global const structs. we can use raw for
that.

holy crap, views might be inlined!

\[ \[ itable0, itable1, itable2 \], objptr\]

instead of the raw pointer to the global const.

yes, if the immutable struct (the itable) is small enough, it can be
inlined.

if there\'s only one or two methods in an interface, it will be inlined.
how cool!

actually, a view could probably point to an \[ objinfopointer, itable\*
\]

that way if the itable is large we can still have the interface info
pointer inlined. but that makes itable pointers rather big\...

and if we really want to go overboard, we can also inline the struct
type info pointer.

the minimum we have to do is make a struct that contains an some type
identifying thing and an object pointer. valefire could take that and do
its own thing (concatenating into a string or something)

valefire could just not include the constant structs anywhere, could
leave them out of the firebase. they just need a deterministic address,
basically. or maybe a mapping can be at the root?

come to think of it, we\'ll have the same problem with storing function
pointers. need a nice deterministic way to represent edges, struct
infos, and functions. i suppose the globalName could work, but its so
huge.

we require a number for every struct and interface, we could require a
32b number for any function whose address is taken.

for edges, it could be the struct and interface mashed together into
64b.

so, this struct would contain a raw pointer to an edge. a global.

what if we give every global const thing a number? functions, global
structs, everything will have one. then, a raw pointer to any of those
things could be interpreted by valefire as just the number it\'s
pointing at.

but if we have a templated struct, giving it a number is meaningless.

we might have to stick with globalname. perhaps we can have a map at the
root between the global name and a local number. also, we can still
number structs, and they\'ll be used in the global name instead of giant
strings. heck, in firebase we can even just make that an integer array,
or lots of nested arrays.

\...this is a hard problem\...

maybe we should make them have a specific file containing a mapping of
instantiated struct to integer? maybe we should have it somewhere in v?
using 7 MarineList = List:Marine;

let:T structNumber:Int;

let structNumber:List:Marine = 7;

support undefined for deleting things in {merge:true} sets.

fix \"Error: Every document read in a transaction must also be
written.\"

.18/100000

18 cents per 100000

if a client does 500 reads, then .09 cents

12 cents per gig to use cloud storage

we can put placeholders in the schedule, which are very broad.

for example, if we know that instruction 5 will need to set a Base\'s
color to 9 but we don\'t know which base, we can tentatively say we\'re
locking every base.

but, if the instruction after that wishes to read a Convoy, then it
shouldn\'t be blocked by that.

so, as more information becomes known, instructions can relax the locks
they have on things.

can we specify somehow that borrow count decrements can be thrown around
like crazy? can we special case that into the scheduler?

perhaps there can be a special \"unique set\" that guarantees no
conflicts ever happen, and we can reorder writes as much as we want?
thats basically what these ref count objects are.

also, any deletion needs to come after all borrow ref count decrements.
we can categorize these by type though.

can we share data between superstructures? this is probably important
for views.

we\'d just need to move its version from inside the structure to into
the big hash map.

so\... if i had a superstructure of superstructures, i\'d probably want
to deeply observe the child superstructures right? maybe? and be able to
incorporate effects on those, via the parent? man, thatd be
complicated\...

if so, every superstructure needs to share the same ID space. only
possible with UUIDs. perhaps we would hand in an ID generator.

superstructure provide three things:

\- copying (snapshots, sharing)

\- comparing (comparing effects)

\- observability/interceptability (request and effect observers)

other approach to views is to do it dynamically. a view will just
compose in the original superstructure as a private implementation
detail really, and then will translate incoming requests to the
underlying one.

is there a way we can do that easily?

interesting, in that way it\'s more like a proto service. the only
things defined in there are the things needed in the API. it can
re-expose things from the underlying one.

so, it\'s kind of like\... a superinterface. which means somewhere we
have a superimplementation lol.

should we have immovable objects?

might be useful for inlining things in firebase

copyable would go well with these

fn \"main:lam1\":(ref T)(T x) {

}

can we pass templatas across function boundaries?

we need T to be a templata, which when forced, lowers to a callable.

but how can a function parameter be a templata?

well, maybe we can make the Parameter2 contain the struct, but into the
environment we can put T -\> templata?

in c++, we just have to have the struct and we\'re fine. then we put the
\_\_call\'s into the environment. how would we do that here\...

well, main:lam1 is a terry to begin with. when we apply more things to
the terry, we can introduce the \_\_call

wait, dont terrys only work for templates and lambdas without closed
variables

so a terry is kind of like two things, it\'s some additional environment
things, it\'s some filtering template arguments, and it\'s a struct
type.

the templata route is proving problematic because of this function
boundary stuff.

lets explore the type route a bit further. we\'d use OrdinaryClosure2
and TemplatedClosure2, and not have the templatedlightfunctionlambda
thing.

how do we get rid of the global function group?

lets make that a type too. it would be backed by a void, lol.

in gc, we should have a header above the thing, which contains a
function pointer to the destructor. or, a vtable, whatever.

for RC, every time a register goes away or a variable goes away, we
should have a Dealias3 instruction which contains the destructor
pointer, if it\'s needed.

Dealias3 will go through and do all the decrements, and for the shared
stuff, the rc=0 checks and possible destructor calls.

There should be no Destruct3 for mutables. Templar should insert those
as regular function calls. after a destruct there should be a dealias
which goes through and does the same thing as it does for shared stuff.

fn revive(player: &Player, life: Life) {

  player.lives.push(Life(lifeId, time))

  if (player.lives.length \> player.infections.length) {

    player.status = \'human\';

  }

}

fn revive(playerId: int, lifeId: int, lifeTime: int) {

  insert into Life (owner, id, time) values (playerId, lifeId, lifeTime)

  IF ((select count(id) from Life where owner = playerId) \>

      (select count(id) from Infection where owner = playerId)) THEN

    update Player where id = playerId set status = \'human\';

  END IF

}

create table Life (

  owner: int, // can be optimized to have a foreign key constraint on
player

  id: int,

  time: int

)

every table should have a column of who owns it.

sql can do while as well\...

= can be used for maps

\[=\] means an empty map (like how swift has \[:\])

\[\"abc\" = 4\]

though, this means we can\'t really have = as a value or an infix
function. im fine with that. if someone wants an assignment operator,
they can use \<- instead.

difference between (Int\|Bool\|Str) and \[Int\|Bool\|Str\]

lets call the first one a union, and the second one a variant.

a one element union (Int) will simplify to Int.

a one element variant \[Int\] isnt actually a variant, it\'s a sequence.
so, the api for a variant has to be a superset of the api for sequence.

.0 of a variant will return the underlying union i think. .1, .2, etc.
will just be an error? or perhaps it can return an optional?

request fn bet(game: &Game, bettingPlayer: &Player, chipsToBet: int):
Result\<Void, Str\> {

  if (game.whoseTurn != bettingPlayer) { return Err(\"Not your turn!\");
}

  if (chipsToBet \> player.chips) { return Err(\"You don\'t have enough
chips!\"); }

  player.chips = player.chips - chipsToBet;

  game.pot = game.pot + chipsToBet;

  game.whoseTurn = players.nextChild(bettingPlayer);

  game.lastBettingPlayer = bettingPlayer;

  return Ok();

}

request fn check(game: &Game, checkingPlayer: &Player): Result\<Void,
Str\> {

  if (game.whoseTurn != bettingPlayer) { return Err(\"Not your turn!\");
}

  if (checkingPlayer != game.lastBettingPlayer) {

    game.whoseTurn = players.nextChild(bettingPlayer);

  } else {

    let winningPlayer = (compare hands, get winning player);

    winningPlayer.chips += game.pot;

    game.pot = 0;

    game.lastBettingPlayer = null;

  }

  return Ok();

}

and it would produce these effects

we might need += and -= and \*= and stuff.

it would make it much easier to capture the semantics of these
mutations\...

or we can do transactiony stuff?

eventually want syntax like this:

struct Tile {

  symbol: Str;

}

struct Position {

  row: Int;

  col: Int;

}

fn main() {

  let mut playerPosition = Position(0, 0);

  let board = MutArray(20, {(row) MutArray(20, {(col) Tile(\".\") })});

  while true {

   foreach board as row {

    foreach row as tile {

     print tile.symbol;

    }

    println;

   }

   mat getKey() {

      if Keys.UP {

       mut playerPosition = playerPosition.copy(row:
playerPosition.row - 1);

      }

      if Keys.DOWN {

       mut playerPosition = playerPosition.copy(row:
playerPosition.row + 1);

      }

      if Keys.LEFT {

       mut playerPosition = playerPosition.copy(col:
playerPosition.col - 1);

      }

      if Keys.RIGHT {

       mut playerPosition = playerPosition.copy(col:
playerPosition.col + 1);

      }

      if Keys.Q {

       ret;

      }

   }

  }

}

  private def coerceGlobalFunctionGroupToPrototype(env:
LocalEnvironment, temputs0: Temputs, globalFunctionGroup:
TemplataGlobalFunctionGroup) = {

    val TemplataGlobalFunctionGroup(functionName,
alreadySpecifiedTemplateArgs) = globalFunctionGroup

    val ordinaryBanners =
env.globalEnv.ordinaryBanners.getOrElse(functionName, List())

    val functionTemplates =
env.globalEnv.functionTemplates.getOrElse(functionName, List())

    assert(ordinaryBanners.size + functionTemplates.size == 1)

    val (temputs1, prototype) =

      if (ordinaryBanners.nonEmpty) {

        val banner = ordinaryBanners.head;

        temputs0.functions.find(\_.header.toBanner == banner) match {

          case Some(function2) =\> (temputs0, function2.header)

          case None =\> throw new RuntimeException(\"wat?\");

        }

      } else {

        FunctionTemplar.evaluateTemplatedLightFunctionFromNonCallForPrototype(env,
temputs0, TemplataFunctionTerry(Some(env), functionTemplates.head,
alreadySpecifiedTemplateArgs))

      };

    (temputs1, prototype)

  }

make it so Construct2 has a structDef in it. the only things that need
struct refs are the structs themselves.

get rid of the constructortemplate in structtemplar. make it so if we
cant find a function, but there exists a type, it puts in a NewStruct2
node. that way, users can define their own constructors.

change TemplataStruct/InterfaceTemplate to a TemplataCitizenTemplate

change StructTemplar.getStruct/InterfaceRef to a getCitizenRef

there\'s a \*ton\* of repeated code with struct and interface.

can come out of packs/scrambles/blocks:

\- values

\- reference expressions

\- templated lambdas

\- ordinary lambdas

cant come out of packs/scrambles/blocks:

\- address expressions (cant say mut (x) = 6, that\'s a pattern not a
subexpr

\- global function groups

the only possible things that could return addresses are local lookups,
closure lookups, and member lookups. and those can\'t be in packs or
scrambles or blocks.

also can\'t come out of packs or scrambles or blocks: global function
groups.

change templataordinarylambdafunction to just be the struct. let the
overload templar figure out whose \_\_call to call. perhaps do this when
we do all the interface stuff.

whoa, if we\'re sure that something is within something else\'s
lifetime, we can use raw pointers for it!

computed properties could be cached, but they have to come with an equal
operator?

to select the foo overload that takes in no params, and returns a bool:

foo():Bool

to select the foo overload that takes in two ints and returns a void:

foo(:Int, :Int):Void

struct var_array {

   size u32

   data \[size\] i32 provides \[\]

}

vale maps:

\[I32:Bool\]

if we ever do anything interesting at runtime based on a ref count (like
modifying a string instead of copying) we might not be able to defer RC
increments with the wingman.

same goes with the interesting compiler optimizations where the callee
increments the stuff we return, we need to be careful about that.

there might be a smart way to go about this\...

sealed trait Effect {}

struct Death impl Effect {}

struct SubtractDrillers impl Effect {}

struct Move impl Effect {

  newPosition: Vec2;

}

// produces a thing

pure fn tick(convoy: &Convoy): Effect {

  let distanceToTarget = distance(convoy.position,
convoy.target.position);

  = if {distanceToTarget \< 1) {

      = Death();

    } else {

      let newPosition = convoy.position + convoy.direction;

      = Move(newPosition);

    }

}

there will be a List\<&ConvoyId\> table. or, more accurately, there will
be a BorrowConvoyListEntry table, containing (listId INT, convoyId INT)

pure fn tick(convoy_list: Set\<&ConvoyId\>): Effect {

  // convoy is the ID of a list, referencing the above table.

  // parentheses means itll be inlined.

  let (r1) =

    (SELECT [[c.id]{.underline}](http://c.id) convoy, c.position_x,
c.position_y, c.position_z

      FROM BorrowConvoyListEntry bcle

      WHERE bcle.listId = convoy_list

      JOIN Convoy c ON bcle.convoyId = c.id)

  let (r2) =

    (SELECT [[c.id]{.underline}](http://c.id) convoy, b.position_x,
b.position_y, b.position_z

      FROM BorrowConvoyListEntry bcle

      WHERE bcle.listId = convoy_list

      JOIN Convoy c ON bcle.convoyId = [[c.id]{.underline}](http://c.id)

      JOIN Base b ON c.targetId = b.id)

  let distanceToTarget =

    (SELECT r1.convoy, sqrt((r1.x - r2.x)\^2 + (r1.y - r2.y)\^2 +
(r1.y - r2.y)\^2)

// or r1.convoy, DISTANCE(r1.x, r1.y, r1.z, r2.x, r2.y, r2.z)

      FROM (r1)

      JOIN (r2) ON r1.convoy = r2.convoy)

  // now assemble a bunch of closure structs for them

  // now do the condition for all of them

  // now do all the things that match the then body (perhaps even check
if there are any?)

  // now do all the things that match the else body (perhaps even check
if there are any?)

}

yeah, i daresay that if functions are pure, this works nicely.

want an IUnit table. it\'s just a table of a bunch of IDs.

we can just make it super clear that any variable they make will
basically be a temporary table. if they dont want one, they should use
variables. pretty reasonable.

\...if statements will be a problem\... and loops that mutate\...
basically the same problem as cuda.

returning a boolean.

basically, if a lambda is \"abortable\", then itll have a secret boolean
parameter that means whether or not we should skip to a special
destructors block which then also returns skip.

i wonder if this is optimizable?

every lambda is abortable.

only certain other functions (if, match, while, etc.) are abortable.

if a lambda has a break or a continue or a return in it, it\'s
considered \'aborting\'. when we give an \'aborting\' lambda to an
\_\_if, it knows, and has an alternate version. same with \_\_while and
\_\_match.

null is nice, but option is nice in a different way.

a world where ? means null:

let x: ?Marine = Marine();

let x: ??Marine = Marine();

the ?? makes no sense because if we assign null into it, which null is
it?

a world where ? means optional:

let x: ?Marine = Some(Marine);

let x: ??Marine = Some(Some(Marine));

makes sense. but we have to put Some everywhere, bleh.

you know, this reminds me of the difference between pack and tuples. you
cant nest packs, you cant nest nullables. you can nest tuples, you can
nest options.

(?Marine) could mean a nullable. (?(?Marine)) would flatten to (?Marine)

\[?Marine\] could mean an option? \[?\[?Marine\]\] would not flatten.

things in past versions of the chronobase are frozen.

things in only the current version of the chronobase are changeable.

fun fact: things in only the current version of the chronobase are also
unique! nothing else can ever point to them.

so, lets use these rules:

\- if something\'s unique, you change it in place (obviously)

\- you can make something unique into something immutable (freezing)

\- (for reverting) you can \"uni cast\" something from immutable to
unique, IF you are the only owner (checked at runtime).

when you use .updated on an immutable thing, the compiler figures out if
it\'s unique, and if so, updates it in place.

we should have a .\_\_updatedInPlace method that makes sure it\'s a
unique pointer.

an interface ref in templar will become a View.

MyInterface would become View:MyInterface.

struct View:MyInterface {

  etable: edge ID?

  obj ptr: raw pointer?

}

lets not specify the etable format.

a View:MyInterface will secretly be transformed by the concretizer to a
\"viewed thing\" which can either contain a raw pointer, or another
struct.

InterfaceToInterfaceUpcast will become AdjustView and have an interface
id.

StructToInterfaceUpcast will become MakeView and have an interface id
and give an expression that gives a reference to a struct. midas is
allowed to inline it if it uses the implementor hint.

so, the concretizer

shoot, we cant test for a trait because if that trait is implemented
twice by a struct, we\'re screwed\...

lets just pretend that doesn\'t happen for now.

right now, all closures are mutable, and have addressibles for every
field.

we should make it so non-mutable fields are references instead of
addresses.

perhaps also consider making immutable closures which can be handed
around freely?

we\'d need a concept of mutable and immutable closures\...

can immutable closures have borrow refs?

()!:Bool would be an owned mutable closure?

&()!:Bool would be a borrowed mutable closure

():Bool would be an immutable closure

blocks should never be immutable, should always be borrowed.

well, except when they capture no variables. then they can be immutable
lambdas.

to the user, immutable structs deeply contain immutables. thats when we
should make a lambda immutable.

lambdas can be owning or immutable.

what if instead, we represent lambdas in interfaces like in java? that
sounds way easier.

in fact, then the imm/mut decision can be made by the interface itself.

right now, in ExpressionTemplar, we make an owning closure struct
instance and then immediately lend it. we need a way to keep it alive
until the end of the statement (or the call that uses it)

when calling a function, mandate that the references are moved from the
arguments. then the callee will mandate that they\'re moved from the
arguments.

the nice thing about this setup is that if we move the last holder of a
shared ref in, then it arrives with 1 and then the receiving function
can just decrement it and throw it away right then, or proceed to use
it.

if you want to copy an imm into a call and retain it afterwards, you
have to increment it.

if we receive it, and move it off to a sub function\... no increment
needed.

maybe we can annotate them as borrow vs move? if you borrow an imm, then
no incrementing or decrementing needed. if you move an imm, then\...
also no incrementing or decrementing needed. but if you try and give it
to a call that moves and then also wanna keep it, you gotta retain it.

need a ShareOwn and ShareBorrow ownership.

how do we know whether a function wants to borrow or move? leave it up
to programmer?

its a question of\... when do we want to free this?

borrow -\> borrow = +0

borrow -\> own = +1

own -\> borrow = 0

own -\> own = 0

own -\> own but i still want it = +1

maybe we can have some analysis thing that checks all your functions and
picks the right one?

if ((obj-\>rc & 0x80000000 == 0) \|\| ((obj-\>rc & 0x7FFFFFFF) \< 2)) {

  nonatomic

} else {

  atomic

}

if youre eventually sending it somewhere, you want an own. if youre
eventually putting it into a struct, you want an own. otherwise, you
want a borrow.

so, we can start everything as borrows but then percolate owns downward
perhaps\...

if we want to revert to a version, use that version\'s map, but have
that version + 1 (or just increment the current version number
probably). that way, the frozen things can stay frozen. no need to
unfreeze.

if we want to temporarily move ahead, then maybe we can lock it, and
make some sort of new structure in front of the original.

or, we can have an unsafe \"unfreeze\" that makes sure theres only one
reference pointing to it.

still, the unfreeze approach is terrible because we\'d have to
reinterpret_cast an immutable tree into an unfrozen tree. that will take
forever. maybe it can do the checks deeply in debug mode, and just
reinterpret_cast in prod mode?

no, the best solution is the first one, even if it is a bit more
expensive. it also means we don\'t need an unfreeze! and frozen things
can be given to the sidekick thread for management.

(used for discord)

spare gmail:

[[kestrelakio@gmail.com]{.underline}](mailto:kestrelakio@gmail.com)

Kalland7

fn do(callable) {

  callable()

}

fn main() {

  let x =

    if (something) { \... }

    else { callSomeSuperSecretFunction() };

}

template\<typename T\>

auto do(T&& callable) {

  return callable();

}

int main() {

  do(\[\](){ return 3; });

}

lets have an ability to \"import\" a contained struct into our own
namespace. it\'s basically inheritance.

(is this the same thing as forwarding? related? similar?)

to the user, we can treat tuples the same as arrays? both are just
\"sequences\" or \"tuples\" or heck even \"array\"s.

they even both use \[\].

seq: \[int, str\]

array: \[10 int\]

in fact, if we have something all of the same type, like \[0, 1, 2, 3,
4\] then there should be no effective difference between \[5 int\] and
\[int, int, int, int, int\].

we should be able to flip back and forth between equivalent tuple and
array?

no. we should convert all uniform tuples into arrays.

1-length tuples should be 1-length arrays.
