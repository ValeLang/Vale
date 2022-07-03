for now, it's called ivytree, "interconnected value-y tree"

ivytreecontroller ClientWorld for World { }

ivynode World for World {

games: !Map:(GameId, Lazy:Game);

}

ivynode Game for World {

players: !Map:(PlayerId, Player, {\_.id});

groups: !Map:(GroupId, Group, {\_.id});

chatRooms: !Map:(ChatRoomId, ChatRoom, {\_.id});

}

ivynode Player parent Game {

name!: Str;

customizedStuff: PlayerCustomizedStuff;

}

wildwest ivynode PlayerCustomizedStuff parent Player {

flair!: Str;

imageUrls!: List:Url;

}

ivynode Group parent Game {

owningPlayer: &!Player;

players: Set:&!Player;

}

ivynode ChatRoom parent Game {

id: Int;

membersGroup: &!Group;

transcript: !Transcript;

}

ivynode Transcript parent ChatRoom {

messages: !OrderedMap:(MessageId, Message, {\_.id});

}

ivycontroller ClientTranscript model Transcript for ClientWorld {

fn shouldMutate(this, :InsertMutation:(Transcript.messages)(index,
message)) {

// return true or false

}

fn afterMutate(this, :InsertMutation:(Transcript.messages)(index,
message)) {

// do any needed removals

}

fn afterMutate(this, :InsertMutation:(Transcript.messages)(index,
message)) {

// show an alert

}

}

ivynode Message parent Transcript {

player: &Player;

text: String;

}

in the ivynode, you either have say ie 'root World' or 'parent
ChatRoom'. If you specified a parent, it will infer the world from that.

**Restrictions**

An ivynode can either contain another ivynode, or an immutable data
structure.

**Any modification is transformed into a mutation, which is given to the
controller first.**

mut myMessage.text = "edited message!"

is transformed into a

SetMutation:(Message.text)(&!myMessage, "edited message!")

and given to the root.

SetMutation:(Message.text) is a subclass of IMutation.

The root can either reject it or let it go through.

**Modifications can be grouped into Mutations.**

ivymutation AddPlayerToGroupMutation

fn addPlayerToGroup(player: &!Player, group: &!Group) {

mut player.numGroups = player.numGroups + 1;

group.players.!add(player);

}

When someone calls:

addPlayerToGroup(myPlayer, myGroup);

it automatically fills the AddPlayerToGroupMutation struct with the
arguments here, and sends the mutation to the root. The root can either
reject it or let it go through.

Then, the mutation is run, and the individual modifications don't go
through the root.

ivytrees by default only allow mutation functions. specific ivynodes can
allow freeform modifications with the wildwest keyword.

Can define the mutation yourself, too:

ivymutation AddPlayerToGroupMutation {

player: &!Player;

group: &!Group;

}

**Nodes can have controllers**

A mutation:

-   Will be approved or rejected by the root

-   Will be approved or rejected by each controller listed in its
    > arguments. (If someone wants additional approvals they can go and
    > call them themselves)

-   Will call the root's beforeMutate method

-   Will call the beforeMutate methods of each controller listed in the
    > arguments

-   Will apply the mutation

-   Will call the afterMutate methods of each controller listed in the
    > arguments

-   Will call the afterMutate method of the root

Controllers and root can use these to update their own internal
client-only states, caches, etc.

There can be multiple types of the same ivybase. They must all share the
same model types, but they can have different controller types.

ivycontroller ClientTranscript model Transcript for ClientWorld { ... }

ivycontroller ServerTranscript model Transcript for ClientWorld { ... }

Controllers can have things like animation curves, or UI state, or
private labels for things. We might choose to not do anything in
reaction to an object's model changing; maybe the controller has the
current world position, and the model is just the "end result". That
way, we can smoothly animate to whatever the server tells us.

**Serializability**

Add the serializable keyword to the ivytree to make everything
serializable.

This will make the language do a thing where every ivynode and mutation
has an ID.

IDs are generated sequentially so it should all just work out.

However... don't keep mutations around or theyll become dangling.
Deserializing a mutation might fail because of this (deserialize returns
an Option)

**Querying**

Queries can be compiled at compile time, optimized, and made into
regular code. Super fast. See
[[Querying]{.underline}](https://docs.google.com/document/d/1ts-MvaVjzo5AIk3mVqsu8YyHNKZVurisEBXt93wAw10/edit)
doc.

**Uses of the plain ivybase**

-   Implement a sanity checker.

-   Store and recall an entire document, which the rest of Vale can't do
    > (the rest of vale has sockets and file pointers and pipes and
    > threads and all sorts of unguaranteeable things).

-   Record all mutations since the beginning of time. Can
    > rewind/fastforward to a specific point.

-   Observe changes to specific parts of your model, with the
    > controllers and/or a path trie at the root.

**Upgrades, combinations**

Consistent model:

-   For **snapshottable** - need chronobase

-   For **broadcasting**, need:

    -   Sendable:

        -   Network: need serializable

        -   Threaded: need serializable or can send chronobase snapshots

    -   For **multi-user**, need:

        -   Retrying/Failing - Client knows what it was trying to do, it
            > can try to phrase it in a different way, or it can just
            > fail and revert. Can't do offline editing here, needs
            > constant connectivity.

        -   For **client-side editing**, need:

            -   Recorded mutations.

            -   Rewindable. Considerations:

                -   Restart the entire app - lol

                -   Linear - need reversible. Linear on number of
                    > mutations. If you have state in your controllers
                    > which you can't just recalculate from the model or
                    > outside world, you \*might\* need this. It's also
                    > pretty fast for client-side editing.

                -   Diffing - need chronobase. Linear on differences,
                    > which is always faster than linear on num
                    > mutations. But, expensive if we need a snapshot on
                    > every single mutation...

IDM "It Doesnt Matter" collaborating and offline editing - Phrase
mutations in such a way that it's impossible to have conflicts.
SetFeatureIndex will always succeed no matter what. Mutating a feature
will always work, even if it's deleted. Deleting a feature will always
work, even if it's been deleted. Everything is mergeable. MM has it so
every mutation is like this, but it was tricky and involved a lot of
sidestepping. This is the only thing that can allow offline editing. The
absolute worst case is if a mutation fails, and then we have to flush
away all pending offline edits. If a mutation can't be phrased like
this... perhaps disable it in offline editing? Offline editing - Need
IDM mutating.

You can add **snapshottable** via a chronobase.

Mutations need to be:

-   Sendable:

    -   Network: need serializable

    -   Threaded: need serializable or can send chronobase snapshots

-   Guaranteed success (no return value, no throwing)

If we want client-side editing, we need either:

-   Only chronobase (slow)

-   Only reversible mutations (fast)

-   Chronobase and reversible mutations (fast)

Without reversible, we have to take a snapshot on every single mutation,
extremely expensive.

IDM is extremely fast, and the only way to have freeform offline
editing.

Atharia needs to be:

-   Rewindable, thats basically it

Could use either. Chronobase would save space probably.

Subterfuge needs to be:

-   Recordable, so we can send to the client all the mutations that have
    > happened.

-   Rewindable on the client side. We'd be jumping around a lot, so we
    > might benefit from chronobase.

-   Sendable over network

-   Serializable because sendable over network

-   Collaborative because our mutations affect others' models.

-   Failure/retrying policies because collaborative

-   Client-side editable so we can play around with plans

Subterfuge would probably need a chronobase, but because of its linear
scanning it could do either

MyMaps needs to be:

-   Recorded because wildwest rewindable

-   Serializable because yes

-   Wildwest rewindable to restore old versions

-   Sendable over network

-   Collaboration

-   IDM mutating

-   Client-side editable

-   Offline editing

MyMaps would be backed by a chronobase

HvZ needs to be:

-   Serializable because yes

-   Wildwest rewindable

-   Sendable over network

-   Collaboration

-   Failure/retrying policies because collaborative

HvZ would only need reversible, not chronobase. Though, would be cool
for graphs\...

Move mutations are scary. What if we're both offline, and I send a move,
and they send a delete? My stuff isn't moved. I have to throw all my
things away.

We probably need to specify all the contents in the move mutation.

**Chronobase**

Keyword chronicled will make the ivybase into a
[[chronobase]{.underline}](https://docs.google.com/document/d/1UB8lC26h7KTOa6t6VAvIGU14y7jldB8WAY7LuBUPUuM/edit#).

**Classes**

We'll need special classes that can handle all of this and do it fast.

List

Vector

OrderedSet

UnorderedSet

OrderedMap

UnorderedMap

And have mutations for all of these.

### V2 Ideas

**Path can be calculated**

An object can report its path, which is a list of field numbers. This
path can even be formatted into a slash path or a polymerish path.

One can loop over the path to get all the objects in the path. Or one
can start at the bottom and do parent parent parent all the way up?

**Controller mutation bubbling**

If the bubbling keyword is present\...

Can call the should/before/afterMutate methods on all nodes all the way
up. It gives the whole path, and what index in the path we're currently
at.

(low priority because cant see a compelling use case for it yet)

### Discarded Ideas

**Mutations can have controllers**

this kind of violates the point of controllers though... this needs
rethinking. maybe if we store an immutable return data from
beforeMutate?

Perhaps this can be accomplished with something like:

ivymutationcontroller AddPlayerToGroupMutationController for
AddPlayerToGroupMutation {

\...

}

or we can just subclass it.

Also, this conflicts with the fact that mutations can be serialized.
Probably get rid of this.
