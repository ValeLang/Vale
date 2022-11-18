in firebase we should have a different table for all the concrete types.
we can listen on all the tables at once.

We can back a chronobase onto firestore.

For now, we can just mirror to a firestore so that others can read, but
only have one machine writing.

The writer machine will send new additions to the /model root, and also
a /mutations root.

a client with a full chronobase can load from /model, and listen to
/mutations. a client that just wants to read/display can get by with
just loading the /model.

Later on, we can make our wildwest mutations into a dozen or so
firestore functions. we'll also have a /controllers root. between these
two things, we can implement wildwest mutation merging.

even later on, we'll also compile our transactions into firebase
functions. that way, we can distribute writing among multiple machines.

we can store a /transactions root, if we wanted to see a history of
things that happened to the model. we'll probably not use it in any of
the reconstruction algorithms, and just stick to /mutations.

## Simple Approach

struct Game {

marines \@Index(0) :Set:Marine;

}

struct Marine {

target! \@Index(0) :?&Marine;

}

we can get around the whole alternating paths problem in firestore by
putting everything in the top level, really\...

option A:

if a unit has a map of unit targets, then what it really has is an
owning reference to a certain map

that map is a document in the top level, with a subcollection of
entries.

each entry is in the top level too.

(id is in the path)

\_\_isdeleted - true if deleted, otherwise false. you cant refer to
this.

\_\_owner - id of the thing that owns this, or \"root\" if this is the
root

\_\_referer_count - number of things refering to this

\_\_collider - randomly generated int, to trigger transaction retries.

this means theres going to be many levels of requesting things\... we
\*could\* do some crazy thing where we store, in every owner, the things
it transitively owns. that\'s a lot of entries\...

option B:

we/have.paths.that/look/like.this

have.paths.that is a collection, which is inside \'that\' which is
inside \'paths\' which is inside \'have\'.

downside: can\'t move.

option A2:

once we detect that we\'re caught up and in a consistent state (should
probably do so with a children count and a hash) we can just listen for
incoming mutations and do the changes locally. however, this makes the
mutations list into a massive hotspot. well, maybe not\... if two things
are unrelated, their order can be interchanged.

is there any reliable way to know we\'re caught up? i imagine if
mutations are flying in all over the place, things can get nasty. the
document will never be in a completely globally frozen state. we need to
just check local consistency perhaps? holy crap this is hard.

we would need to check:

\- every owned object knows its owner

\- every borrow is up to date\... how!? perhaps some accumulated xor of
borrows?

when we borrow something, it must be in memory. things that aren\'t yet
visible can borrow things that are visible but not vice versa. which
means we cant really do the xor thing\...

option A3:

The server has a solid working version, kept consistent via
transactions. it also keeps a list of mutations. periodically (every
minute?) it will take a snapshot of the entire database. when a client
wants to read, it gets the version from at least a minute before the
current version, and then it applies mutations to stay caught up.

BOOM. it works!

also, fun fact, we dont need \_\_isdeleted, \_\_owner,
\_\_referer_count, or \_\_collider in the snapshots.

we should definitely keep the undo mutations around, because the order
of mutations coming in will be quite erratic.

also, the mutations list shouldnt be one gigantic one, it should be per
object.

actually, there\'s a problem. lets say we are granted access to a chat
room. we need a way to use up-to-date permissions so we can get the
data.

ok, lets say, access to old chat rooms depends on new permissions?
should also depend on new \_\_isDeleted\...

yes, otherwise we could access old data that we\'re not supposed to.

so, lets say we are given new access to a chat room. we would know about
it (it would appear in our player info) and then we would know to
request it, and we would receive it because the permissions are current.

lets say we have access taken away. we\'ll be notified by a change in
our player info, and we know to unsubscribe.

lets say it just gets deleted. we\'ll be notified by a change in our
player info.

this fits really well with the whole borrow-ref-brings-it-in approach.
eerily well.

it is kind of odd though, that the player has a central list of all
documents available to him. its the opposite of the google model, where
the document stores a predicate of who it\'s readable to. is there a way
we can make that particular model work?

perhaps if we think about it that way, then the player\'s list is just a
way to communicate to the player about the existence of some chat room.
the player has to be notified \*somehow\*\... so, perhaps we just dont
care how its made known to him. it could be some sort of weak pointer,
or integer even. they can trigger the load themselves. but if we do the
borrow-ref-brings-it-in approach, then it forces them to load it
immediately.

can make a bunch of different tables in firestore; normalize it. can
just listen to those tables. then we can have some more tables for past
versions. and some more tables for controllers. thatll make things
somewhat easy to migrate off.

but really, it\'s not that hard to just put everything in one gigantic
table\...

struct IntOriginRange {

size: Int;

}

fn \_\_len(r: &IntOriginRange) { r.size }

fn \_\_elem(r: &IntOriginRange, index: Int) { index }

fn map(r: &IntOriginRange, lam: ():Void) {

\_\_arrmap({\_\_len(r)}, {\_\_elem(r, \_)}, lam); // or can that first
arg just be \_\_len ?

}

fn range(size: Int) { IntOriginRange(size) }

\_\_arrmap will call \_\_len and \_\_elem on the input range thing, and
fill an array with the stuff.

get the element from the source at this index?

what about iterators?

if we had an iterator, then we would keep iterating through it

\_\_arrmap would take two lambdas: hasnext and next.

arrmap needs to know the size up front. bleh.

void\* \_\_arrmap(int size, lambda getter, lambda transformer)

template\<typename Iter, typename IsEnded, typename GetCurrent, typename
Next\>

void \_\_arrmap(Iter&& current, IsEnded&& isEnded, GetCurrent &&
getCurrent, Next&& next) {

if (isEnded(current))

}
