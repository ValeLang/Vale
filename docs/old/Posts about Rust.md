we have to decide whether we are writing:

-   a defense post, to explain how vale is different than rust

-   a post to convince rustaceans to use it (yeah right lol)

-   a marketing post, explaining to non-rustaceans, mostly c++ and app
    > devs, why this is awesome and you should try it. also explaining
    > **why** rust failed and how vale solves it

-   a technical post about when to use rust and when to use vale

-   a post to ingratiate ourselves to the rustaceans, so they know
    > we\'re not competing and theyll wish us luck

-   a post explaining rusts influence on vale

must also learn why we\'re doing any of this benchmarking. ah yes, to
compare to c++ mainly, and then have a ready answer when folks compare
us to rust.

\"it clicked. i suddenly knew how to use lifetimes and the borrow
checker. that was when i learned idiomatic rust. it was an addicting
feeling, like solving a puzzle. i felt smarter writing rust.

\...however, after a while, the good software engineer in me started
noticing things. i was exposing implementation details in certain
places. i was making things public which really shouldnt have been. i
was passing parameters around that gave me access to much more than i
needed. i was centralizing things in unhealthy ways, for no better
reason than to make the borrow checker understand it. i was flattening
data that was more understandable as hierarchical. sure, the memory
safety was nice, but honestly, C# gave me memory safety. i didnt feel
like it was worth it to throw away very important engineering principles
for only an extra few percent of speed.

its not like i didnt have a choice\... i could have fallen back on Rc.
but that felt like giving up, and its pretty universally agreed that
Rc/RefCell is a red flag. after all, if im going to use Rc/RefCell, I
might as well be using a newer, much more ergonomic language like Nim or
Zig, or go back to C#.

its been a long time, and i havent seen much improvement in this area.
rust doesnt support encapsulation and abstraction well. its not
multi-paradigm, because it doesnt support OO (even the good parts). it
has one hammer, which we call \"idiomatic\", and we use it for every
nail.

perhaps the problem was in me, perhaps i was using the wrong language
for the wrong task.\"

\- anon on HN somewhere

if they ask for details, talk about how yew exposes the entire world to
all its handlers

\"rust cant completely replace c++. rust can take the low level. vale is
aiming at the high level.\"

maybe a reddit post asking \"can we do OO in rust? im \*not\* asking if
we \_should\_ do OO, and i\'m not asking if we should do OO \_in rust\_.
im asking, can we, and how well does it work?\"

blog \"Taking lessons from rust into a new language\"

make the EC vs ECS article into a blog post. ultimate goal: show people
that ECS and data-oriented design isnt always the answer.

post on the subreddit with the monadic backflip, ask if theres a better
way to do this.

also, move the code to a different project in ValeLang, its a bit weird
here.

rust is not multi paradigm; it cant do OO.

doublecheck rust global allocators

compare

mention that the region borrow checking was inspired by rust

when people say \"you can do interior mutability in rust\", use [[this
explanation]{.underline}](https://www.reddit.com/r/ProgrammingLanguages/comments/i0pnc6/zero_cost_references_with_regions_in_vale/).

\"Rust\'s safety and speed, without the borrow checker\"

run time checking for mutable aliasing:

\- rust: refcell and generational indices

\- vale: constraint refs and weak refs

compile time optimization:

\- rust: borrow checker

\- vale: compile time RC

post abt low level access != speed

alloc heavy progs better in gc

\"a higher level rust that\'s much faster to code in\"

\"an easier, higher-level, more flexible rust that can cross-compile\"

It may seem scary to halt. However:

\- Rust does this with its aliases, which take the form of generational
indices, which we often expect! to point to a live object.

\- Assertions are a thing!

\- It only happens in debug mode.

\- We can whitelist and continue.

blog post about how vale superpowers are opt in.

expert rustaceans might be able to do the most efficient thing quickly.
i can do the most efficiemt thing quickly in c++ too. but we\'re
experts.

it feels like, with both c++ and rust, you need to gain a lot of
knowledge and skill before you can do something.

this is why its so cool that we have regions being opt in. the
complexity happens if you need safety and speed both. if just need
speed, use fast mode, its easy. if just need safety, use resilient mode,
its easy. only if you really need to optimize, add regions.

multithreading is an optimization btw.

\"rust makes you pay a hefty price, but it also encourages you into very
optimal code. all the time. even when you dont want it, and would
consider it harmful premature optimization.\"

\"by default, vale gives you safe code thats a balance between easy and
fast. then, when you have profiled and identified which parts are slow,
you can speed up those areas with:

\- inl everywhere! and make the compiler remind you.

\- implicit locking

\- bump calling

\- pool allocators

\- pool calling, where we have a pool for every type. perhaps this can
be a giant array of all types used in that particular region, with a ptr
to each for which pools we want? can be an abstraction over the bump
allocator really. maybe even use a hash map where we hash by struct id.

and pour effort into there, instead of everywhere else.\"

talk to the yew folks about the problem theyre solving, ask if it can be
a more general solution to this problem in rust. ask if they see this
being as pervasive as the next guice

\"i hope rust continues to rise in popularity and spread to more
programmers. it really is a wonderful language for a lot of use cases,
such as writing drivers or constrained environments like shaders or
embedded systems.

vale aims to be the best choice for everything else where we need
performance, such as games, servers, and apps.

there are use cases that rust should not be used for. it should never be
used to interact with the DOM, for example. it cant even attach an
observer to a button without massive architectural damage.

vale has no such restrictions.\"

allocators and regions show us ownership neednt be tied to allocation

in the rust trait vs enum battle, consider what
[[https://crates.io/crates/downcast-rs]{.underline}](https://crates.io/crates/downcast-rs)
says, user-defined things may want to downcast back to themselves

vale is like rust but easier. borrow checker is opt in, so we can apply
it to areas that we decide to spend extra time on.

\"rust prioritizes low level access and speed over architectural
freedom\"

write an article aimed at rustaceans. explicitly say up front vale isnt
competing with rust, its seeing if we can take rust principles to higher
levels.

\...maybe say this after we publish cross compiling?

rust is not good at oo. but figure out how to say that without saying
the word oo. then ask r/rust if they agree.

somehow get an honest discussion going where we talk about rusts
strengths and weaknesses, and whether its a good or a bad thing to
shoehorn rust into places its not supposed to be

maybe even say that you think rust \*can\* do these things, for example
returning a lambda, or using rc/refcell.

Within C++, there is a much smaller and cleaner language struggling to
get out

-bjarne

a post that says:

\- rust brings a lot to the table. it taught the world a lot of things:

\- generational indices are amazing

\- a borrow checker can constrain the lifetimes of references

\- you \*can\* do things immutably then apply an effect (may have come
from functional languages)

rust took the world by storm, especially low level programming, and
data-oriented applications.

the borrow checker is a means to an end. it gives us speed and safety,
by forcing us into certain patterns, such as generational indices

it also gave us inheritance that doesnt suck, and decoupled structs from
their interfaces.

it also gave a breath of fresh air to concepts from c++ (and earlier)
such as single ownership and zero cost abstractions

our goal with vale was to do a truly cross-compiling language, which was
only possible with single ownership. however, we are also fans of rust,
and wanted to harness its ideals.

rusts has reached great heights, and made tradeoffs that really nail its
intended use case: low level programming, and data-oriented programming.

like any language, its not perfect, and those tradeoffs came at a cost.
the mutability xor aliasability rule makes things difficult with traits,
and cripples OO, a very important aspect of app development. but then we
stepped back and realized, we could take the lessons from the borrow
checker, the means to the end, and deliver the ends.

(also say the other things rust cant do, but gotta be 1000% convincing)

\- generational indices baked into the language.

\- region borrow checking to speed things up

\- (not really cuz of rust) instead of only having weak refs and unowned
refs, we added c refs because its good to get early feedback about
someone needing your object.

different tradeoffs for different places. we hope youre as excited as we
are to see rust influencing more areas of the world!

make a blog post from the EC vs ECS post

post about rust vs vale:

-   highlight all of the great things about rust

    -   thread isolation allows rc w no atomics

    -   gen indices

    -   better OO.

    -   borrow checker can lead to cleaner code because a lot of it is
        > immutable (when doing composition rather than dod)

-   then say vale is doing all those, without borrow checker

rust does OO right.

advertise \"composition over inheritance\" and maybe have an article
laying out the case that its good to be flexible in what paradigms you
can support

\"this isnt idiomatic rust\"

this is how rust does an EC architecture.

i know rust uses more data-oriented design and ECS, but this is a
benchmark comparing languages, not architectures. like i said in the
article, there will be another benchmark comparing both of the languages
in an ECS architecture.

\"trying tl see hlw far we can go in rs w blah, without worrying about
whats \'idiomatic\'. idiomatic is great, until it keeps you from
experimenting. i find the concept limiting and short-sighted.

in vale, when we have regions, try EC+arrays and show how its no faster
than EC.

then write about how EC gives you more fexibility or something? dunno.

maybe EC+arrays is better cuz we can loop over all components in the
level\...

less hash lookups?

better cleanup. when you kill a unit, its components go away
automatically. in ECS, you have to clean up everything manually.

however, in EC, i had registrations and maps for that kind of thing,
which was extra state to keep track of. perhaps we can resolve this by
having a constraint ref from the component to the unit?

in ECS though, you can iterate over all of a certain thing on a level
(not in mine, all units are in the global map, but if we did store em
per level)

in rust, can use RegionBox\<\'pool, T\> to do something like vale region
annotations (see
[[https://www.reddit.com/r/ProgrammingLanguages/comments/i0pnc6/zero_cost_references_with_regions_in_vale/]{.underline}](https://www.reddit.com/r/ProgrammingLanguages/comments/i0pnc6/zero_cost_references_with_regions_in_vale/))

ask him if theres anything like typed-arena but that can pool and reuse
per arbitrary type. dont see a way without opting in per struct type,
like with the \'pool region, the special class, and youd need something
like
[[https://docs.rs/unique-type-id/0.1.1/unique_type_id/]{.underline}](https://docs.rs/unique-type-id/0.1.1/unique_type_id/).
its not decoupled at all.

(presumably it also might work like a refcell)

one big downside i can see is that you have to specify \<\'pool in every
struct. it doesnt really decouple region from the struct.

also cant put someone elses refs in there and expect it to be sane.

also it can \*only\* be used in a region now, you cant just randomly
make one of these things.

also, it cant do any interesting things such as pointer compaction

also, good luck moving that across a boundary and seceding it.

also im not convinced rust can have a type ID to group pools by. it also
definitely cant automatically shove things out into a yon to guarantee
some safety.

Verdagon:

hey yall, i could use a pointer

Verdagon:

so, im experimenting with an EC architecture in rust

Verdagon:

like unity does with its MonoBehaviours

Verdagon:

and it\'s working, and i think its pretty interesting, but it does use
dyn which ive heard is a code smell

Verdagon:

first question, is dynamic dispatch always a code smell? or is it just
dyn?

():

neither are always a code smell

():

often there are times where it\'s not actually needed, but it has its
place

Kixiron:

Dynamic dispatch has uses

Verdagon:

i\'d imagine that its main use is for when you want, say, a user of your
library/framework to be able to define their own structs which could
implement a trait you give them

Verdagon:

because an enum can\'t encompass all possible subclasses of all possible
users of your library

Verdagon:

is that about right?

Kixiron:

It's generally discouraged because static dispatch is faster, but
there's places where it's needed

Kixiron:

Eh, not necessarily

Verdagon:

that makes sense to me; its a smell from the optimization perspective

Kixiron:

It's more for operating on things from which you have no info on

Kixiron:

That's the whole point of it really

Kixiron:

You know exactly one thing about dyn Trait: you can call the methods of
Trait on it

Kixiron:

The underlying thing can be 1 byte or 10 thousand, made by you or by a
dependency of a dependency of a dependency, known at compile time or not

Kixiron:

But it doesn't matter because you can call the vtable functions on it

Verdagon:

so, if i have a game engine that i\'m shipping, and i want users of it
to be able to plug in \"components\" onto an entity (like an EC
architecture), and have those components respond to four or five
different events

Verdagon:

that seems like it would count

Kixiron:

Yah, that's generally a good use case

Verdagon:

however, if i knew up front what all the possible components were, id
use an enum

Kixiron:

Generics can work though, depending on your api

Kixiron:

This is a better scenario for generics than an enum

Kixiron:

Enums are not easily extendable, the are by definition a fine
enumeration of possibilities

Verdagon:

yeah! my friend just mentioned something like that, where i accept a
\<T\>, and the user can specify their own Box\<dyn IUnitComponent\>

Wolvereness:

This sounds like the Lua problem\...

Verdagon:

whats the lua problem?

Kixiron:

Generics have most of the advantages of dynamic dispatch without the
disadvantages

Verdagon:

mmm i see what you mean

Kixiron:

You get monomorphization and all that jazz

Wolvereness:

It\'s where you have extensions to a game written in Lua instead of the
original language

Verdagon:

oh\... i was imagining rust extending this IUnitComponent trait

Kixiron:

And yah, if you put an ?Sized bound on the generic you can accept
dynamic dispatch

Wolvereness:

Super common in gaming

Verdagon:

so, now we get to the very interesting question

Kixiron:

?Sized means you don't know the size though, so you'll have to indirect
it somehow, be it reference, Box, Rc, Arc or other

Verdagon:

and dont murder me, i also dont like how this is structured, but im
about to show you some code which you will not like

Kixiron:

Plus you have to make the trait object safe which can be a pita

Verdagon:

so, i have this list of components here:
https://github.com/ValeLang/Vale/blob/master/benchmarks/BenchmarkRL/rust/src/tile.rs#L36

Verdagon:

so im exposing ITileComponent to the user, so they can throw them on
tiles as they want

Kixiron:

Btw, you don't need to name traits as interfaces

Kixiron:

That's not really rust style

Verdagon:

thanks for the tip =) i dont work with others\' rust code that much,
good to learn

Kixiron:

I'm not entirely sure what you're using this downcast thing for though

Kixiron:

Why do you want a random T from it?

Verdagon:

anyway, im having trouble letting the user modify the overall world from
within the ITileComponent subclass

Verdagon:

yeah, thats another thing i was gonna ask yall about, we\'ll get to that

Kixiron:

You'll need to pass in the world somehow

Verdagon:

yeah =\\

Verdagon:

and i found a way

Verdagon:

which i dont like (because its weird) but also like (because figuring it
out made me feel good hahah)

Kixiron:

Which generally means storing the operators separately from the world

Kixiron:

They shouldn't be mutating each other anyways

Verdagon:

i was able to use an old trick from functional land, a bit of monaddery

Verdagon:

https://github.com/ValeLang/Vale/blob/master/benchmarks/BenchmarkRL/rust/src/fire.rs#L20

Kixiron:

Neat trick, actually

Verdagon:

basically, returning a function that can mutate the world

Verdagon:

i doubt its idiomatic rust, but it actually accomplishes all my goals

Verdagon:

im wondering if theres a better way to do it though

Kixiron:

Storing the components separately from the world

Kixiron:

Having them operate on it instead of being part of it

Verdagon:

that sounds like its deviating from OO / EC and getting more into ECS
territory

Verdagon:

which isnt bad

Verdagon:

but it\'s unfortunate i cant do OO like this

Kixiron:

Rust isn't an OO language

Verdagon:

is it not? i keep hearing mixed messages on that

Verdagon:

i think it could be a good oo language

Verdagon:

i suspect theres some language additions we could add to actually make
this situation pretty nice

Kixiron:

Nah, it's not traditionally OOP

Kixiron:

There's inheritance but it's purposefully different

Kixiron:

Rust is more data oriented and functional, it derives a lot from SML

Wolvereness:

OOP is usually an anti-pattern, and rust basically won\'t let you do the
worst of it

Kixiron:

Think of structs not as objects, but collections of data

Verdagon:

OOP is an anti-pattern in general, or rust OOP is an anti-pattern?

Kixiron:

Ownership extends to how you group things, not just using them

Wolvereness:

In general, it\'s used badly

Verdagon:

yeah, rust is pretty amazing at the data oriented way of doing things

Kixiron:

Both

Verdagon:

hmmm i think the \"in general\" part isnt universally true

Kixiron:

But anyways, don't try to make rust OOP

Kixiron:

It's a trap a lot of people fall into and it's really counterproductive

Verdagon:

i hear you

Verdagon:

so tell me more about what the rust way would look like here

Kixiron:

OOP usually groups by action, and rust by ownership of that makes sense

Wolvereness:

OOP programming works great if you treat it as behaviors and data, but
too often they get mixed. Meanwhile in rust, you get traits and
structs/enums respectively

Verdagon:

i would have a separate Arena\<Box\<dyn ITileComponent\>\>?

Verdagon:

at the level of the entire game, im imagining

Verdagon:

or perhaps not even have a containing game struct

Verdagon:

usually data-oriented means i\'d have a bunch of parallel arrays

Verdagon:

but im not sure how to do that in a user-extensible way

Kixiron:

Well, making the world and the component storage separate

struct World { .. }

struct Components {

components: Vec\<dyn Component\>,

}

trait ITileComponent {

fn run(&self, world: &mut World);

}

matt1992(multindex!(arr;4,19..)):

An arena of Box\<dyn ITileComponent\> doesn\'t sound that useful, the
Box still allocates the dyn ITileComponent using the global heap.

Verdagon:

\@matt1992(multindex!(arr;4,19..)) good point, and i think Rusky told me
about a way we can have pointers into an outside pool, which would be
more efficient

Verdagon:

lets assume they arent on the heap for now

Kixiron:

I don't really mean data oriented like the actual paradigm, I mean
grouping things by what information they hold

Verdagon:

i see!

Kixiron:

Like, not holding a field that's supposed to operate on the struct as a
whole

Verdagon:

ok leme think through this

Verdagon:

i could accept a &mut self now

Kixiron:

Because you can't do that nicely

Kixiron:

Yep

Verdagon:

if i wanted to modify this ITileComponent

Verdagon:

i see how this solves it

Kixiron:

It's a definite mental shift that can be really hard to get used to

Verdagon:

heh, ive done this architecture before, the mental shift for me is
learning what architectural freedoms rust allows and doesnt allow

Verdagon:

what happens if an ITileComponent wants to remove itself from the Vec?

Kixiron:

I can't from C# so I did this many moons ago

Kixiron:

Have an unregister function or something

Verdagon:

but i have a &mut reference to an element inside its larger Vec

Verdagon:

so it cant be modified

Kixiron:

Even better actually, an is_registered function

Verdagon:

that was another reason i had to add a lambda \>\_\>

Verdagon:

hoping i can avoid it with the more data-oriented approach

Kixiron:

Then before running each component, ask them if they're registered

Verdagon:

oh! i think i see where youre going with this

Verdagon:

ask each component beforehand whether it wants to remove itself

Verdagon:

and basically run a .filter with that

Kixiron:

Yah, you could do that

Kixiron:

I was more thinking keeping them all, but just selectively not running
some

Kixiron:

But that depends on what you want to do, really

Verdagon:

yeah

Kixiron:

Whether or not components can come back or whatever

Verdagon:

are you sure i should give up on doing OO in rust?

Verdagon:

im a little\... sad about that

Verdagon:

OO is a great option to have in the toolbox

Kixiron:

pie_flavor\'s better at explaining OOP and rust's relationship than I am

pie_flavor:

hi

Kixiron:

The parts of OOP that rust doesn't mesh with are usually inheritance and
self-modification in weird ways

pie_flavor:

Rust steals the best features of functional languages, like typeclasses
and sum types, without itself being functional. In the same way, Rust
steals the best features of OOP, like methods and interfaces, without
itself being OOP.

pie_flavor:

The thing that Rust very emphatically does not steal is virtual
inheritance, which is the backbone of OOP

Kixiron:

Trait inheritance is purposefully neutered

Verdagon:

virtual inheritance, like c++\'s virtual base classes?

Verdagon:

or like java\'s base classes?

pie_flavor:

those are both virtual inheritance, yes.

Verdagon:

got it

pie_flavor:

OOP languages use virtual inheritance to solve four distinct problems.
Several variants of one type, several types with a common interface,
using one type like another, and code templating.

pie_flavor:

What doesn\'t fit either of those is usually fantastically awful to
maintain.

Verdagon:

preachin to the choir =) my ideal language offers those separately

pie_flavor:

Rust instead solves each of these problems separately. Enums for several
variants of one type, traits for several types with a common interface,
Deref for using one type like another, and macros for code templating.

Verdagon:

i want to believe that, with these separate tools rust offers, i can
still do OO in rust, just a better more disciplined and streamlined
version than java/c++

pie_flavor:

I am not sure if that\'s the speech I\'ve been called upon to give, or
if it was instead the one about units of responsibility

Verdagon:

one that\'s more pure OO, rather than the abomination that java and c++
inflicted on the world

Kixiron:

You're just more familiar withpie_flavor: love your speech

Kixiron:

It's been years for me

Verdagon:

if there was a throwing flowers reaction, id use it lol

pie_flavor:

OO tends to be a solution in search of a problem.

Verdagon:

would you say that OO has its benefits and drawbacks, which sometimes
make it the appropriate paradigm for a given situation

pie_flavor:

Most of its \'design patterns\' are ways around the lack of features in
OO languages, and most of its utility is in solving those four problems.

pie_flavor:

Yes.

pie_flavor:

The problem is that people treat it like a paradigm they should attempt
to use whenever possible, rather than just whenever it fits best.

Verdagon:

amen!

pie_flavor:

For example, Vec is a well designed object.

pie_flavor:

but trying to change the formatting system to be object-oriented would
be painful.

Verdagon:

that people default to common base classes for code sharing is a mortal
sin that should be punishable by extreme nagging

pie_flavor:

yes. Macros are your friend.

Verdagon:

so, im in an awkward spot here

Kixiron:

Or traits, depending

Verdagon:

lets say i found a situation that i believe OO is particularly well
suited for

Verdagon:

i think im running into a problem where rust itself isnt letting me use
the four tools to pull off OO

pie_flavor:

the four tools are not meant to \'pull off OO\'

pie_flavor:

they are meant to solve problems.

Verdagon:

hmmm yeah, id agree with that

pie_flavor:

OOP is not something to be sought, it\'s just a tool in one\'s toolbox.

Verdagon:

the problem trait is supposed to solve is to be able to call methods on
a type you know little about

pie_flavor:

however, if you\'re steeped in OOP wisdom, you will often design your
data structures in a way that makes them hard to use in Rust

Verdagon:

and give the user freedom to plug in whatever they want (assuming static
dispatch isnt possible, because its usually preferable)

pie_flavor:

in OOP, objects semantically do things

pie_flavor:

and they are the only things that do anything

pie_flavor:

and so the common design principle for your data structures is to make
an object contain another object if it is responsible for it, i.e. if it
\'manages\' it or uses it for its implementation.

pie_flavor:

This leads to all sorts of annoyance like borrow splitting and a struct
borrowing itself.
