Lazy loading can save a ton of bandwidth. On top of that, it means we
can load parts of the model.

And by putting restrictions on what parts of the model we can load, we
suddenly have security.

Virtualizing native code? Like what android does, either by fork, or by
using a embedded VM

ivytree ZedsModel of World;

ivymodel World for ZedsModel {

games: !Map:(GameId, Lazy:Game);

}

ivymodel Game for ZedsModel {

players: !LazyMap:(PlayerId, Player, {\_.id});

groups: !LazyMap:(GroupId, Group, {\_.id});

chatRooms: !LazyMap:(ChatRoomId, ChatRoom, {\_.id});

}

ivycontroller GameController for Game { }

ivymodel Player for ZedsModel {

name!: Str;

}

ivycontroller PlayerController for Player {

\@Parent game: GameController;

}

ivymodel Group for ZedsModel {

owningPlayer: &!Player;

players: Set:&!Player;

}

ivycontroller GroupController for Group {

\@Parent game: Game;

}

ivymodel ChatRoom for ZedsModel {

id: Int;

membersGroup: &!Group;

transcript: Lazy:Transcript;

}

ivycontroller ChatRoomController for ChatRoom {

\@Parent game: &Game;

\@Read({ userCanAccessChatRoom(userId, game, this) })

\@Write({ userCanAccessChatRoom(userId, game, this) })

// constraints can go here too perhaps

}

fn userCanAccessChatRoom(userId, game, chatRoom) {

// one case where we want to hash assist\...

game.players.find({\_.userId == userId}) in
chatRoom.membersGroup.players;

}

ivymodel Transcript for ZedsModel {

parent chatRoom: &ChatRoom;

messages: !LazyOrderedMap:(MessageId, Message, {\_.id});

}

ivymodel Message for ZedsModel {

parent transcript: &Transcript;

player: &Player;

text: String;

}

We'll need some sort of controller for each person on the server side,
to keep track of what things the user has loaded already. If something's
about to be sent down to them that is a borrow reference to something
they don't know about, then we find that unknown target, look at its
path in the tree, and load everything up that path.

On the server side, their controller for each object can have these
chunks of data per reader.

**Lazy Structures**

Map:(PlayerId, Lazy:Player) is a tricky case. We don't want to load
\*all\* the player ids and have empty lazies for this. We might need a
special LazyMap.

in lazymaps, when you get, you get a future.

it's a special future, you can use it to unload too.

lazymaps can load a certain range

lazylists can also load a certain range

you can tell them to automatically load new things

this will help with chat messages

**Split Mutations**

When User A send a mutation to the server, to modify three players, but
User B only has read access for one of the players, we have a problem.
We can't send the mutation because it contains a borrow reference to
something they can't see.

We allow them to specify what should happen:

-   Policy A: Specify a rule for who the mutation affects, in other
    > words, who can observe changes to the mutation. Everything
    > affected by this mutation must have equivalent rules, such that
    > nobody else observes an effect of this mutation.

-   Policy B: Allow splitting.

Can specify this on a per mutation basis.

**Weak Pointers**

Weak pointers will also try to load what they're pointing at.

**Optimization**

VIndex would actually be cool for the collaborative case, to implement
OrderedMap (ban List!). theyre looked up by ID instead of index, but
theyre ordered, and you want to iterate fast. but snapshotted lists
would probably need to just be a LinkedHashMap.

### Alternatives Considered:

**Have a way to define certain areas that can or can't reference other
areas.**

/games/\*/chatRooms/\*/messages/... can reference /games/\*/players but
not vice versa

But all /games/\*/players must be loaded. If we don't want to load all
players for all games, that will be a problem.

We can be more specific with capturing and paths:

/games/{gameId}/groups/{groupId}/owningPlayer ∈ /games/{gameId}/players

When we encouter a borrow we don't know about, we look in the paths for
what to load.

**Constraints**

Use a \@Constraint or a constraint keyword to specify what this specific
borrow pointer might point to, so we know to load that set.

the 'in' constraints are used to know what to load when we try to load
things.

you can specify your parent by either doing the (game:
Game)/chatRooms/\_/(this) or Game/chatRooms/\_/(this) or the \@Parent
parent: Transcript.

nope. lets get rid of this capturing, and instead just use
this.parent.parent.parent and so on. we can make it shorter by making
getters in our parents to get to what we need.

\@Constraint({ this.myReference in this.parent.parent.chatRooms })

a constraint is now basically a function call, cool! and we can look at
the variables used to figure out dependencies. kind of. its complicated.
its basically like polymer.

wait, this doesnt work, we cant infer what subsets to load from this. we
need to make a special "inconstraint" or something. how about \@In. it
takes a block which uses the current struct as its closed thingies, huh
thats cool. it expects a lazy container to be returned.

**Weak Pointers**

weak pointers wont try to load anything that's not already loaded.

or perhaps... weak pointers can try, but if they get rejected by
security concerns then so be it.

im worried how we're reusing weak pointers to be null in both cases
of 1. the entity's been deleted and 2. we dont have access to the
entity. we should really separate these. perhaps a LazyWeak? Or a
Secured:&&Player? SecuredRef:Player\...

**View**

A view is a subset of the chronobase that a given user can see. In HvZ
people can only see things theyre in the group for. In subterfuge, fog
of war. People can listen to and operate on a specific view, and the
mutations will be sent up to the central model, and the effects will
then filter back down through the view.

The nice thing about this is that even though only a 'visibleToPlayer1'
bit might be flipped, the entire object looks like it's deleted to
player 1.

Should be on a per-object basis.

Option A, filtering:

-   Every player has a list of visible entities.

-   Any time any visible objects changes, it should run the object
    > through the read rules for it and all of its ancestors. if any of
    > them say false, then the change should not be broadcast to the
    > user, instead the object should be removed to the player.
    > otherwise, the change should be broadcast to the user.

-   Any time any invisible object changes, it should run the object
    > through the read rules for it and all of its ancestors. if all of
    > them say true, then the object should be added to the player.

Option B, diffing:

-   Whenever we take a snapshot, we make a set of all previously visible
    > entities, and a set of all currently visible entities. This is a
    > subset of all objects that have changed since then.

-   Diff through all changed objects.

-   Run the read rule on every one, ancestors first. For each:

    -   If was visible and is still visible, broadcast any changes in
        > this object's properties.

    -   If was visible and isnt anymore, send a removal and don't keep
        > descending.

    -   If wasnt visible and is now visible, keep descending and
        > afterwards send an add.

    -   If wasnt visible and still isnt, then do nothing.

If you already had a view in memory for every player, then this could be
extremely fast. Subterfuge could just do all of its querying here, be as
expensive as it wants, and we only run anything once.

Or, if you make the read rules really simple (read a bool in the model
that the programmer calculates and puts in every snapshot) then it could
be extremely fast.

If anything in a view has a borrow reference to something outside of the
view, it's an error. We could use Lazy:&Something perhaps?

Queries would have to be done on the view i suppose.
