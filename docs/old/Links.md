## Links

A link is a reference that's bidirectional; the object can know of any
link that's pointing to it.

struct Player {

enemy link : &Goblin;

}

(could call it "bidi" instead of "link")

it makes that &Goblin into a Link:(T, C), whose last member is a &Goblin
and has a next and a previous pointer.

struct Link:(T, C) {

next: &Link:(T, C);

previous: &Link:(T, C);

target: &T;

}

the C is the globally unique "field ID". In this case, C = Player.enemy.
It exists to disambiguate between different fields in my object that all
point at the same target. It also exists to calculate the offset back up
to the owning object.

that link keyword enforces that the Goblin there implements
ILinkable:(T, C)

Goblin, since it implements ILinkable:C can call
getLinks:(Player.enemy)() which returns a list of these things

ILinkable:(T, C) is an interface that just has a link(&Link:(T, C))
method.

theres also a nice Linkable:(T, C) struct which Goblin includes to
implement that, and forwards the call to it.

Linkable has a next pointer that points to the first link, and a
previous pointer that points to the last. if there are none, then it's
just circular with that one fake root element.

when we get a reference to a Goblin, we call goblin.link(myLink)

fn link(this: &Linkable:(T, C), newLink: &Link:(T, C)) {

newLink.next = this.next;

newLink.previous = this;

this.next.previous = newLink;

this.next = newLink;

}

when we get rid of that reference, we call myLink.unlink();

fn unlink(this: &Link:(T, C)) {

this.previous.next = this.next;

this.next.previous = this.previous;

}

we can loop through easily from the root. ~~we \*could\* also loop
through from any of the links, but we'd need to check for that null at
the root every time, because it's not a real node.~~ the pointee
goblin's Link can be just 2 pointers, and leave the target out. every
link except for the root one has a target. this saves a pointer in the
target object. worth it? probably; these can add up.

to loop through the root... first, note that C is globally unique, and
will tell us the offset of the field within the targeter. loop through,
and at each link, subtract that offset, to get to the object's root.

if any kind of unit (there might be hundreds of subclasses) can target a
Goblin, then Goblin suddenly has hundreds of link roots. definitely
unacceptable.

lets say we took out the C. we'd suddenly need a pointer back to the
owning object.

struct Link:T {

next: &Link:T;

previous: &Link:T;

owner: void\*;

target: T;

}

but this still needs hundreds of link roots in goblin, because of the
:T.

lets take it out. we now can't iterate over a specific subclass,
unfortunately...

struct Link {

next: &Link;

previous: &Link;

owner: void\*;

target: void\*;

}

i still feel like there's a way to have separate lists on the target\...
perhaps the target could receive the incoming pointer, and field ID, and
do what it wants with that? it would require a virtual call, but thats
fine.

so, if we want a Goblin to keep track of Players and Goblins targeting
it separately, then it can check on receiving it.

we'd need a way in the syntax to specify to keep track of them
differently though.

Linkable:Player

Linkable:Goblin

struct Goblin {

\...

}

thats kind of elegant.

i cant think of a way to have Goblin keep track of a specific field. If
50 of the 400 subclasses have a "talkTarget" then we dont want to have
to list those out. i think in that case, we'll want a special kind of
reference?

struct GoblinTalkTargetLink {

owner: &Unit;

unit: link Goblin;

}

and then we can say

Linkable:Player

Linkable:Goblin

Linkable:GoblinTalkTargetLink

struct Goblin { ... }

in fact, we'd probably want one of these for everything that targets us.
why not just use these, instead of specifying targeter classes?

struct GoblinArrowTargeter {

owner: &Unit;

unit: link Goblin;

}

struct GoblinTalkTargeter:(T extends Unit) {

owner: &T;

unit: link Goblin;

}

Linkable:GoblinArrowTargeter

Linkable:GoblinTalkTargeter

struct Goblin { ... }

i feel like this can be sugarified:

link GoblinArrowTargeter;

link GoblinTalkTargeter:(T extends Unit);

Linkable:GoblinArrowTargeter

Linkable:GoblinTalkTargeter

struct Goblin { ... }

we could even separate them:

link GoblinArrowTargeter;

link GoblinTalkTargeter:(T extends Unit);

GoblinArrowTargeter links Goblin;

GoblinTalkTargeter links Goblin;

struct Goblin { ... }

does this mean we can reuse these target things?

the thing on the left is a link, and the thing on the right is a...
interface or class?

i suppose both. that kind of works.

should we combine them like this?

link GoblinArrowTargeter links Goblin;

link GoblinTalkTargeter:(T extends Unit) links Goblin;

struct Goblin { ... }

maybe.

whoa. what if... we put data in the link!?

complexlink ArrowTarget {

target: Unit;

turnStartedTargeting: Int;

intentToKill: Bool;

}

simplelink TalkTarget;

ArrowTargeter links Goblin;

i kind of think we should keep the "links" statement in the same file as
the class, at least. for now. cuz it adds so much overhead to the target
class (2 pointers). in fact, lets put it inside the interface/struct
def.

complexlink ArrowTarget {

target: &Humanoid; // this thing must implement ITargetable:ArrowTarget

turnStartedTargeting: Int;

intentToKill: Bool;

}

complexlink TalkTarget:T {

target: &T; // this thing must implement ITargetable:TalkTarget:T. not
sure why we would templatize an edge though.

volume: Int;

}

simplelink AttackTarget Humanoid;

struct Player {

arrowTargetLink: ArrowTarget;

talkTarget: TalkTarget:Int;

attackTarget: AttackTarget;

}

simplelink cant have members, and . will take you directly to the
target's members.

how do we do many-to-many?

struct Player {

targets: List:ArrowTarget;

}

\...done lol.

the owner is the Player, not the list. must be careful about that.

can we put restrictions on the owner? make them implement an interface?
because then we can have the target call methods on the targeters, such
as iAmAboutToDiePleaseForgetMe()

simplelink Forgetter AttackTarget Humanoid;

then the humanoid can loop over all the things that are AttackTarget'ing
it, and know that theyre all Forgetters.

wait... how does the target get at the owner of a complexlink? perhaps
.owner? or .targeter? or maybe we can just require they specify it in
the struct.

complexlink AttackTarget {

owner: &Unit;

target: &Humanoid; // this thing must implement ITargetable:ArrowTarget

turnStartedTargeting: Int;

intentToKill: Bool;

}

so, the first field is always the referer, and the second field is
always the referend.

links only make sense if the target person wants to refer back

this is pretty cool.
