# Honest conversations on the borrow checker\'s benefits and drawbacks, Part 1: Shared Mutability

This is a conversation between two characters who disagree about the the
borrow checker\'s role in a programming language. The truth, of course,
lies somewhere in the middle.

I hope that by the end, you\'ll learn some new borrow checker benefits
and drawbacks, understand their architectural effects, and know when to
follow the borrow checker. Enjoy!

\- Evan

\-\-\-\-\-\-\--

**Blackbeard:** \"Well met, Redbeard!\"

**Redbeard:** \"Well met, Blackbeard! We haven\'t talked since we were
wee deck-scrubbers, learning Rust.\"

**Blackbeard:** \"And here we are now, with many years of Rust
experience behind us. Ladbeard, will you write down what we talk
about?\"

**Ladbeard:** \"Sure thing, cap\'n.\"

> The captains are very similar, they value speed and memory safety, and
> they both hope Rust will thrive and evolve. But they look at it from
> different angles.
>
> Blackbeard thinks the borrow checker is \"the finest innovation in the
> last 30 years, when used for the right things.\" Redbeard thinks that
> the borrow checker is \"a stellar architectural guide, and we must
> stay true to it.\"
>
> Their arguments often devolve into insults and fist-fights, ending in
> good-hearted laughter and ale. It\'s my favorite time of the year!

**Blackbeard:** \"So Redbeard, this year, I propose a twist: let\'s talk
about the borrow checker\'s strengths and limitations. What\'s your
favorite benefit of borrow checking?\"

**Inline data**

**Redbeard:** \"I\'d say my favorite benefit is how the borrow checker
allows us to safely reference **inline data:** a struct that lives
directly inside another struct, or on the stack.\"

**Blackbeard:** \"Aye! That\'s often a necessity for performance; it
avoids an extra dereference which might cause a costly cache-miss. Do
any other safe languages have inline data?\"

**Redbeard:** \"C#\'s structs come close, but you can\'t hold a
reference to them, they\'re copied whenever you access them. And in Java
we can use extends to embed something in an object, but only once, and
it\'s forced public. There\'s a few more, with similar limitations.\"

**Blackbeard:** \"Well said! And what\'s your least favorite?\"

**Unintuitive**

**Redbeard:** \"I\'d say my least favorite thing about the borrow
checker is that it\'s unintuitive.\"

**Blackbeard:** \"This, coming from someone who knows it so well!\"

**Redbeard:** \"No, no, hear me out. I mastered Rust a long time ago.
But I teach it often, and when you teach something, you need to remember
how hard it was to learn. \"Honor your struggle,\" as they say. It may
be simple to us now, but it was difficult to learn back then. That\'s
why I say it\'s **unintuitive.**\"

**Blackbeard:** \"Aye, that is true. There was the occasional genius who
said they understood it instantly, but the rest of us had to work for
it. Why do you think that is?\"

**Redbeard:** \"I assume it\'s because people are coming from languages
like C++ and Java, which taught them to program without a borrow
checker, but I honestly don\'t know; all we have is anecdata on the
subject. What do you think?\"

**Blackbeard:** \"I\'d say it\'s because it **can\'t handle shared
mutability,** which seems very common in real-world situations. If Drake
and Ladbeard each point at Jack, and either can draw an anchor on
Jack\'s face, they both have shared mutable access to him. But the
borrow checker doesn\'t let them both have mutable references to Jack.\"

**Vec and Indices, Effective Shared Mutability**

**Redbeard:** \"But the borrow checker *can* do shared mutability. We
can stuff Jack in a Vec\<Pirate\>, at index 42, and then Drake and
Ladbeard can each remember the number 42. Then, either of them can take
a pirates: &mut Vec\<Pirate\> parameter, do pirates\[jack_index\] to
temporarily get a &mut Jack, and use it to draw hilarious things on his
face.\"

**Blackbeard:** \"Aye, the classic index-into-Vec approach, the
foundation of the Rust empire! It works; there are never multiple &mut
Jack at the same time, so it\'s not shared mutability, but Drake and
Ladbeard do each have an index that they can temporarily trade for one.
\...we need a name for this concept.\"

**Redbeard:** \"HashMap is also a common example, so let\'s call it
**key-based shared mutability**, because we hand a \"key\" (the index)
to a container (the Vec) to get the &mut we want. \[# [[GhostCell and
GhostToken]{.underline}](http://plv.mpi-sws.org/rustbelt/ghostcell/paper.pdf)
also count, even though the container is imaginary.\]

With key-based shared mutability, we need to change the function
signature to **pass in the container,** but we can be certain it
**won\'t crash**, unless we remove elements from the container. That\'s
a very nice property to have!\"

**Cell and RefCell**

**Redbeard:** \"I\'ll also mention, sometimes we can use Cell for things
like this. Perhaps Drake and Ladbeard can each have a &Jack, and Jack
contains a Cell.\"

**Blackbeard:** \"Alas, Cell only works in specific cases, and this
isn\'t one of them, because Drake and Ladbeard might outlive Jack.

What about a Rc\<RefCell\<Jack\>\>?\"

**Redbeard:** \"Rc\<RefCell\<T\>\> has more run-time overhead and panics
than Vec, and is usually a sign that someone\'s working around the
borrow checker. They should avoid them, and work *with* the borrow
checker, like we did with the Vec above.\"

**Blackbeard:** \"But it\'s a valid part of Rust, isn\'t it?\"

**Redbeard:** \"They are, but so is unsafe, and we should generally
prefer patterns that can we can prove won\'t crash. And if one follows
where the borrow checker leads them, they\'ll have a healthier
program.\"

> **Ladbeard:** This is the crux of their differences. Redbeard always
> works within the borrow checker, with the occasional Cell and clone.
> Blackbeard also uses those, but often works around the borrow checker
> too.
>
> Now, we get to today\'s main questions: **What are the benefits and
> drawbacks of the borrow checker? Should we always try to work within
> it?**

**&mut self and Encapsulation**

**Blackbeard:** \"Healthier in most cases perhaps, but not always.\"

**Redbeard:** \"Not always?\"

**Blackbeard:** \"Any function that wants to trade an index for a &mut
Jack must add a &mut Vec parameter, which their callers pass to them.
And their caller\'s callers must pass it to them, and so on. It\'s a
**viral parameter.** Some things become so viral, and so widely
reachable, they\'re **effectively global,** which works against
encapsulation.\"

**Redbeard:** \"Show an example?\"

**Blackbeard:** \"Let\'s say in Java we have a World which owns Planes
which own Missiles, and each Missile has a Plane targetPlane; reference.
And we have a function:

void act(Missile missile) {

missile.position =
missile.position.towards(missile.targetPlane.position);

}

Now, what does this look like in Rust?\"

**Redbeard:** \"Well\... Missile\'s Plane targetPlane; should probably
be a target_plane_index: u64; instead. And we turn that missile
parameter into some indices:

fn act(world: &mut World, plane_index: u64, missile_index: u64) {

let target_plane_index = missile.target_plane_index;

let target_plane_pos = world.planes\[target_plane_index\];

let missile = world.planes\[plane_index\].missiles\[missile_index\];

missile.position = missile.position.towards(target_plane_pos);

}

**Blackbeard:** \"Indeed! But notice how we\'re passing the entire World
in, so we can trade in our indices for a &mut Missile. Now, every
function that indirectly calls this one *also* needs a world parameter.

Rc\<RefCell\<T\>\> doesn\'t have this problem. This is a problem with
key-based shared mutability, with always staying within the borrow
checker: **it introduces viral parameters, and sacrifices
encapsulation.**\"

**Redbeard:** \"Why pass in the entire World? Why not just the planes
array?\"

**Blackbeard:** \"That works for a while, until your planes need to
affect something else, such as a base.\"

**Redbeard:** \"I see what you\'re saying. But did the Java way really
have any encapsulation either? Usually with those approaches, you can
affect the rest of the world indirectly anyway. The viral parameter
approach here is better because the function is more honest about what
it will be modifying.\"

**Blackbeard:** \"The Java way isn\'t perfect; you can affect the world
in some ways, but you can see how it doesn\'t give a blank check like we
see with the borrow checker. And requiring a World-param might be more
honest, but I wouldn\'t say its better; we have to give up some really
powerful patterns.\"

**Redbeard:** \"Really? Like what?\"

**Blackbeard:** \"I\'d say the big ones are observers, dependency
references, graphs, and most RAII.\"

**Dependency References**

**Redbeard:** \"You say the borrow checker can\'t do \"dependency
references\"\... what are those?\"

**Blackbeard:** \"That\'s when an object has a non-owning reference to
some sort of system that it can use to do its job. For example, a
PersonCard might have a &mut PeopleRequester to send a rename request,
which it takes in via constructor.\"

**Redbeard:** \"Oh, dependency injection!\"

**Blackbeard:** \"Aye, but I\'m not talking about frameworks. I\'m
talking about just the pattern of holding a non-owning reference to
affect the outside world.\"

**Redbeard:** \"I don\'t see why Rust can\'t do that.\"

**Blackbeard:** \"The problem comes in when we need two PersonCards
open. Each needs a &mut Requester to make a request, but the borrow
checker disallows both having it.

In other words, if you have a reference to something, nobody else can
change it. If you need to change it, nobody else can have a reference to
it.\"

**RAII**

**Redbeard:** \"Wait a minute, we use RAII all the time!\"

(clarify in here we\'re talking about the advanced kinds, not just
freeing memory)

**Blackbeard:** \"We do! Yet, we have to escape the borrow checker every
time to do it. If you look carefully, every Rust RAII tutorial involves
FFI such as printing, RefCell, Mutex, or unsafe. \"

> **Ladbeard:** Surprisingly, Blackbeard is right. I searched \"Rust
> RAII\", and all the results just [[print out to the
> screen]{.underline}](https://doc.rust-lang.org/rust-by-example/scope/raii.html)
> or other FFI. One [[used a
> RefCell]{.underline}](https://aloso.github.io/2021/03/18/raii-guards.html).
> The closest attempt makes a [[smart-pointer with a
> deleter]{.underline}](https://www.reddit.com/r/rust/comments/g3qfi1/comment/fntg1le/?utm_source=reddit&utm_medium=web2x&context=3)
> but only works for a given scope so is more like try-finally than
> RAII.

**Redbeard:** \"How can it not have RAII?\"

**Blackbeard:** \"It\'s because in RAII, an object will affect the
outside world. To do that, an object needs to either take in a &mut
parameter, or hold an &mut member. But [[drop() can\'t take parameters
to the outside
world]{.underline}](https://doc.rust-lang.org/std/ops/trait.Drop.html),
and the borrow checker generally doesn\'t let an object hold onto &mut
members, except in trivial examples.

The fundamental problem is that **RAII requires shared mutability**, but
Rust needs extra parameters (like &mut World) to do shared mutability,
which we sometimes can\'t do.\"

**Observers**

**Redbeard:** \"I\'ll have to think about that. Meanwhile, what do you
mean Rust can\'t do observers?

**Blackbeard:** \"Same reason, really. Take a click observer, defined by
some GUI library:

trait ClickObserver {

fn click(&mut self);

}

We\'d need to take a &mut World as a parameter to modify anything, but
we can\'t modify a trait from another crate.\"

(perhaps move this elsewhere)

**Redbeard:** \"I feel like your design is fundamentally flawed. If the
borrow checker disagrees with it, it\'s probably an anti-pattern. You
should probably refactor so that you don\'t run into this problem.\"

**Blackbeard:** \"Are you saying that RAII and observers are also
anti-patterns, then?\"

**Redbeard:** \"Hmm. Maybe. I\'d never thought about those, I just kind
of followed the borrow checker and what other people said, and it worked
out well enough.\"

> **Redbeard:** \"But how do GUI frameworks like Yew do this?\"
>
> **Blackbeard:** \"Their solution is basically to add a full
> message-passing framework to communicate between components.\"
>
> **Redbeard:** \"Seems like a rather extreme workaround. There must be
> a better way.\"
>
> **Blackbeard:** \"Let me know if you find one!\"

**Shared mutability**

**Blackbeard:** \"It\'s not all bad though! This is why unsafe and its
safe abstractions exist. We just need to stop treating shared mutability
like a disease. It\'s always been here anyway, in the form of indices,
we just need to figure out the best way to use it.

This is why Rust has both the borrow checker *and* the escape hatches,
like Cell, RefCell, and unsafe. It\'s a beautiful set of tools.\"

**Redbeard:** \"You\'re not suggesting we use RefCell more? And risk
panics?\"

**Blackbeard:** \"We already risk panics, when we index into Vecs.\"

**Redbeard:** \"Not necessarily, we can use [[Vec\'s get
function]{.underline}](https://doc.rust-lang.org/std/vec/struct.Vec.html#method.get).\"

**Blackbeard:** \"We can use RefCell\'s [[RefCell\'s try_borrow
function]{.underline}](https://doc.rust-lang.org/std/cell/struct.RefCell.html#method.try_borrow).
Listen, I\'m not saying we should go drench our code in RefCell. I\'m
just saying, there\'s more tools than just Vec and indices.\"

**Iterator invalidation**

**Redbeard:** \"But that would be opening pandora\'s box! If we say
shared mutability is okay, then people will use it more often, and
we\'ll fall back into the spaghetti-code nonsense of the 90s.\"

**Blackbeard:** \"But Redbeard, you already said that Rust does shared
mutability all the time, with indices.\"

**Redbeard:** \"Yes, but it does it in a safe way, that results in less
bugs.\"

**Blackbeard:** \"How so?\"

**Redbeard:** \"For example, [[iterator
invalidation]{.underline}](http://web.mit.edu/rust-lang_v1.25/arch/amd64_ubuntu1404/share/doc/rust/html/book/first-edition/references-and-borrowing.html#issues-borrowing-prevents)
is only possible with shared mutability, and the borrow checker prevents
that.\"

**Blackbeard:** \"Very true! The borrow checker makes us rewrite our
code so that we use indices, checking on every subscript, or \"purely\"
do all of our modifications later, which are both safer.\"

perhaps add:

-   blackbeard: iterator invalidation is kind of solved in java by a
    > runtime check, which we never run into, why do we care

-   redbeard: iterator invalidation is a specific example of a larger
    > pattern. we\'re using the borrow checker to enforce that array
    > iteration behaves in a \"locally predictable manner\" (and give
    > some other examples not related to loops)

-   perhaps raise local predictability to a first class concept?

**Better patterns**

**Redbeard:** \"Aye! It\'s just one example of the borrow checker
encouraging us into safer patterns. And that\'s because it generally
encourages us away from shared mutability.\"

**Blackbeard:** \"I wouldn\'t say we\'re being pushed away from shared
mutability. We\'re being pushed *towards* something else.\"

**Redbeard:** \"Like what?\"

**Blackbeard:** \"Next time, Redbeard! The sun sets, and the ale calls!
Ladbeard, what did you get out of this?\"

**Ladbeard:** \"Well cap\'n, the borrow checker has its obvious
benefits, like protecting us from use-after-free and whatnot, but
regarding shared mutability, the borrow checker\...

-   Lets us have inline data.

-   Protects us from iterator invalidation.

-   *Can* handle shared mutability, in the form of indices\...

-   \...which can sacrifice encapsulation.

-   Can\'t do RAII, observers, or dependency references.

-   Is unintuitive.

Then Blackbeard hints that maybe we should give shared mutability
another look, beyond just indices.\"

**Blackbeard:** \"That\'ll do, Ladbeard!\"

**Redbeard:** \"To the ale!\"

# Notes

by the end, the battle lines should be between these two paradigms:

-   shared mutability that requires reaching through a parameter to
    > exchange a token (like an index or a ghost token)

-   self-contained shared mutability (like pointers and
    > Rc\<RefCell\<T\>\>)

Points to make by the end:

-   It\'s totally legit to stay within the borrow checker. Less panics
    > and overhead. It **will** lead you to a **good** program.

-   The borrow checker forces things into parameters.

-   The borrow checker can\'t do *real* shared mutability, but it can do
    > *effective* shared mutability. Alas, RAII and observers require
    > *real* shared mutability.

-   Adhering to the borrow checker sacrifices dependency refs and some
    > encapsulation.

-   If we go outside the borrow checker, we can get **bad** programs,
    > but can also get **great** programs.

its really an article for self-contained shared mutability, like RefCell
or FFI.

more, its an article saying that the borrow checker is not enough, it
should live on a foundation of shared mutability.

\"parameter-borrowing shared mutability\"

vs

\"self-contained shared mutability\"

\"I\'d say the speed and safety.\"

\"We all know that, Redbeard, give us something new!\"

\"Well\... inline data then.\"

statically verifiable, cant crash:

phantom data

indexes

cell

all these require you to at least know the target exists. all require
explosive refactoring.

can crash, can use raii, obs, di:

refcell

perhaps this is all more an argument against zero-arg destructors. no,
because obs, di, refactoring. also, we can resolve the zero arg
destructor thing with !Drop, and then make drop take parameters. but
then we still force parameters.

this is an argument against forcing everything through parameters.

have an example from game design.

A attacks B

suspend, B explodes and kills A

resume

do some code assuming A is still alive

the equivalent rust code might be to take in a mutable world, or to push
effects onto a list

ironically, taking in a mutable world wouldnt actually solve the
problem. pushing effects onto a list might also hit some races. which
we\'ll need to add checks for.

so whats the real benefit of the borrow checker here?

probably that it provides a speedbump for these risks.

maybe have blackbeard go on a tangent on how to implement RAII in rust,
and talk about using refcell or unsafe, bring up all the classic
examples (file io, println, etc) and say that this is the benefit of
refcell and unsafe

redbeard can say no, stay within the borrow checker

blackbeard then asks how

and redbeard cant find a way, because its impossible

Part 2: Procedural, Constraints, Refactoring, APIs

**~~(Pure functions are great.)~~**

~~**Redbeard:** \"I\'d say my favorite is how~~ ~~the borrow checker
encourages **pure functions.**\"~~

~~**Blackbeard:** \"But, Rust doesn\'t have pure functions?\"~~

~~**Redbeard:** \"Well, \_effectively\_ pure. Take this function, where
all parameters are immutable:~~

~~fn decideAction(world: &World, player: &Warrior) -\>
Option\<TakeStepAction\> {~~

~~\... // Look for nearest enemy. If none found, return None.~~

~~let path = Vec::new();~~

~~\... // Find path to that enemy. If none found, return None.~~

~~return Some(TakeStepAction::new(path\[0\])); // Return next step!~~

~~}~~

~~Pure functions are good in that you don\'t have to worry about the
arguments changing.~~ ~~And it\'s faster; normally the compiler would
re-load data in case it changed, but now it doesn\'t have to.\"~~

~~**Blackbeard:** \"Aye, and we can still modify objects we created,
inside the function. It\'s the best of both worlds. As far as I know,
not many other languages offer that.\"~~

~~(insert some conversation about their philosophical difference)~~

when does the borrow checker really help?
