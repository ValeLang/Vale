### Wildwest Mutations

synchronized ivytree ZedsModel {

}

wildwest ivymodel Game for ZedsModel {

chatRooms: Set!:ChatRoom;

players: Set!:Player;

}

wildwest ivymodel Player for ZedsModel {

name!: Str;

}

wildwest ivymodel ChatRoom for ZedsModel {

messages: List!:Message;

}

wildwest ivymodel Message for ZedsModel {

player: &&Player;

text: Str;

}

These are okay:

-   Change something's index in a list

-   Append to list

-   Prepend to list

-   Remove from list

-   Insert into set

-   Remove from set

-   Replace

-   Move (property to property, property to set, list to property, set
    > to list, all fine)

Rules:

1.  They have to supply all the IDs of any created objects (so for
    > append, replace)

2.  If they come in at the same time as any other mutation, the end
    > result should make sense, and should make it so we dont have to
    > throw away all subsequent operations, so, offline editing should
    > be good

Never needs to be rewound because everything's guaranteed to succeed.
Offline editing is possible.

is it possible to rewind a networked chronobase? if we keep all the
mutations and just play them in reverse, then yeah, easy. in fact, we
wouldnt need to be backed by a chronobase. chronobase is really for
snapshottability.

we need reversible mutations if we want client side edits, because we
need to be able to replay mutations that the server doesnt yet know
about; in other words incorporate mutations into the past.

wildwest areas can just do diffing. but what does it mean when both of
those things are together?

perhaps we can store mutations at the controller level? the controller
of the lowest common ancestor... and if it's in a wildwest area, dont
even store anything.

the danger is that some mutations will need to do things to the wildwest
areas. if we make it so you can write, but can't read from wildwest... i
wonder if that would work?

network layer

-   if sending enabled, sends incoming mutations to somewhere

-   only sends after the mutations are successfully applied to the inner
    > layers.

-   if receiving enabled, receives them

-   if client-side-prediction is enabled, will keep track of the undo
    > stack.

requirements:

-   if sending across network, then mutations need to have all fields
    > explicitly numbered

-   if sending is enabled, then mutations need to implement resolution
    > strategies

-   if client-side-prediction is enabled, then mutations need to
    > implement inverters

### Broadcaster

A broadcaster is a layer on top of an chronobase (or even a mutabase if
thats a thing) which will fire off mutations to any observer. These
mutations contain the references to the immutable objects. These
mutations can even be observed by another chronobase, which basically
makes a mirror of it across threads.

The obvious implementation would be to fire off when any mutation
happens.

If we're on top of an chronobase, we could instead make it so when we
say .snapshot() it depth-first-searches downwards to find all the things
that have changed (compare version numbers) and broadcasts a massive
diff of all of that. It would send across the thread the new root, plus
the diff containing compacted mutations. This would be O(n) on only
everything that's changed.

This is really only if we need that diff. We could instead just have
sent the new instance. We probably want the diff for the C++ side
though, so it can update its whatevers.

### Mutations

that \@Mutation means that the AttackMutation will go over the network
(or thread), not the individual modifications. The AttackMutation has to
be immutable except for chronobase references.

So, over the network will be sent the AttackMutation, which is just two
IDs and an Int in this case.

This can save a ton of bandwidth.

### Collaborative Mutating

One copy will be the master, and be authoritative. This one doesn't have
to have historical snapshots, but could.

The client will have another copy. This one doesn't have to have
historical snapshots either, but does need its rewind point.

Any local edits on the client side can be made, thus kicking the local
copy into its new HEAD. These edits (or mutations) will be sent across
the network. Then, the mutations will come back from the server. If they
come back identical to the ones sent, then the rewind point is brought
closer to client HEAD. Eventually, it'll be in sync.

However, if a different client, or the server, makes an edit, then it
might come in, and there will be a conflict.

#### Resolution A: Simple

In very simple data, we can do a firebase-like or docs-like resolution
strategy:

-   Remove + Mutate = Remove

-   Mutate + Remove = Remove

-   MutateA + MutateB = MutateB

There will be a few more mutations, such as AddAtIndex, etc.

Controllers would probably be rather minimal, or react on a per-field
basis. Good for things like forms, UI dialogs, or very simple tree-based
data.

Objects might just disappear and then reappear as mutations are
reapplied. We can do a diff between the old client HEAD and the new
client HEAD to see what changed, and inform the controllers of that. The
controllers need to be aware that these things changed, and needs to be
able to react to random property changes. This is probably the fastest
when there's a large number of tiny random edits everywhere.

#### Resolution B: Rewind + Retry

We rewind to the rewind point (where the server mutation came in) apply
it, and apply all mutations that came after that. If they don't operate
on the same data, then things are fine.

(We should also have a way to bake in constraints into the data, and
check those constraits any time anything changes)

However, applying like this might be impossible because the underlying
data changed from what the mutation expects. In this case, we'll give an
error associated with that mutation. The client code can issue a
different mutation, or give up and leave the world in that state.

The controller will simply be informed that the data has been rewound,
and then it will be informed of the new mutations being applied.

#### Resolution C: Reverse Mutations

We can require that all mutations be able to generate their inverse.
Then, the controllers can react to mutations, instead of data changes.
This is probably the most powerful option.

We can provide some tools that make sure the inverses are done
correctly.

#### Combinations

Each tiny edit can be a mutation. Perhaps we can whitelist those as
mutationable, so we don't just randomly do edits willy-nilly.

That way, we can update things like a player's position incredibly fast,
and with very low chance of conflict. But, things like death can be big
transactions.

In HvZ, the addPlayerToGroup would be a mutation, but adding a chat
message would be a regular modify.

### Constraints

We should have constraints like in MySQL, which could help detect when
conflicts should be resolved.

Or perhaps we can keep an integer inside each object saying the latest
mutation that read it. If another mutation comes in that hasn't
acknowledged at least that version, then we know a conflict happened.

we have foreign key constraints... perhaps we can expose a hook for
custom constraints, such as sanity checks! BOOM.

we can make this work in firebase, we'd just need a ton of space.

we have a separate list\<string, bool\> liveObjects; at the root. only
allow a mutation through if the ID exists in that map. perhaps it can
even be a version number sort of thing. 1 would be the first version.

in fact, this could basically be the controller, it would have the
parent objects and so on.

i feel like firebase might not be well suited for this though... its
optimistic caching stuff is a little scary. we might need to have the
server update a certain bit in the controller before clients consider
the thing stable.
