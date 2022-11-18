(temporary name for the vale compiler written in vale, self-hosted)

each stage can be distributed across various machines.

they can collaboratively build an environment:

case class Environment(

variables: Map\[String, Variable2\],

functions1ByOrdinarySignature: Map\[Signature2, Function1\],

ordinaryBanners: Map\[String, Set\[FunctionBanner2\]\],

functionTemplates: Map\[String, List\[Function1\]\],

typeMembers: Map\[String, ITypeTemplata\],

valueMembers: Map\[String, ITemplataValue\],

impls: Map\[String, List\[Impl1\]\]) {

**Scan Structs, Impls, Interfaces**

First, we scan and find all the templated/ordinary
structs/impls/interfaces and build up a graph. note, we wont actually
evaluate any of the templated structs, impls, and interfaces. we're just
scanning for them. This can be done wild-west style.

(fun fact: additions are always safe in a wild-west environment, if you
dont iterate, dont check existence, and only get futures)

**Process Structs, Impls, Interfaces (continuous)**

Then, we fire up stampy machines for the structs. each name will be
given to a machine. Each machine will wait to try to claim an unclaimed
name.

we process every struct, and whenever we want to get a certain stamp of
a certain thing, we ask that machine for a stamp of it. that machine
adds it to the temputs, which is ALSO MIRRORED! so basically we fire off
a request and wait for another machine to add it to the temputs.

**Scan Functions**

Scan for all templated/ordinary functions. This will involve stamping
new structs/impls/interfaces, as we figure out ordinary function params'
types.

**Process Functions**

Fire up stampy machines for the functions. Each name will be given to a
machine. Each machine will wait to try to claim an unclaimed name.

we process every function, and whenever we want to get a return type of
a function, or ensure it makes it into temputs, we ask its owning
machine to do it.

that machine adds it to the temputs.

there will be a lot of waiting for other machines to do their work, but
that's fine. each machine can have multiple threads working, and when a
thread is stalled, another thread can start up.

**Carpenter**

Assemble edge tables, and figure out covariant returns, and so on. This
can easily be divided up.

**Midas**

Midas is much easier than templar, it doesn't rely on other machines to
do its work, just the initial inputs.

**Sculptor**

Sculptor can also be split up really easily.

**Deadlocks**

We need to detect deadlocks. Perhaps before a thread waits on a future,
it can signal that it's waiting, and some text explaining. That way if
we get to a cycle, we can gather the explanations and give them to the
user saying theyre a cycle.
