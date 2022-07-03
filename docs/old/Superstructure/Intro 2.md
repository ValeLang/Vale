Long ago, assembly programmers realized they were repeating certain
patterns, and made languages for those patterns, for example C, with its
functions and structs.

We used these structs and functions and soon realized a pattern in how
we used them; we often had functions to read from and modify structs.
C++ and Java were designed around that pattern, to give us the
\"class\".

Without structs and functions, we wouldn\'t have discovered classes. We
had to learn to \*think\* in terms of structs and functions, and only
then could we see the pattern and discover classes.

A programming language makes it easier to use these patterns, but it
also lets us think at a higher level. Now that we \"think in classes\",
we can see the patterns in how we use them. One such pattern is the
superstructure.

We often have a group of structs at the heart of our program, sometimes
called the \"model\", and we also have structs that represent changes to
the model, which we\'ll call \"requests\".

For example, a poker game\'s model might be:

> enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }
>
> struct Card {
>
> suit: Suit;
>
> number: int;
>
> }
>
> struct Player {
>
> hand: List\<Card\>;
>
> chips: int;
>
> }
>
> struct Game {
>
> players: List\<Player\>;
>
> deck: List\<Card\>;
>
> pot: int;
>
> lastBettingPlayer: ?&Player;
>
> whoseTurn: ?&Player;
>
> }

And we have a "bet" function which is something like this:

> fn bet(game: &Game, bettingPlayer: &Player, chipsToBet: int) {
>
> bettingPlayer.chips -= chipsToBet;
>
> game.pot += chipsToBet;
>
> game.whoseTurn = players.nextChild(bettingPlayer);
>
> game.lastBettingPlayer = bettingPlayer;
>
> }

We often find ourselves making a struct like this (for sending over the
network, or for logging, or plenty of other reasons):

> struct BetRequest {
>
> bettingPlayerId: Int;
>
> chipsToBet: Int;
>
> }

Or, depending on our particular needs, we might define the "effects":

> struct PlayerChipsAddEffect { playerId: Int; toAdd: Int; }
>
> struct GamePotAddEffect { gameId: Int; toAdd: Int; }
>
> struct GameWhoseTurnSetEffect { gameId: Int; playerId: Int; }
>
> struct GameLastBettingPlayerSetEffect { gameId: Int; playerId: Int; }

This is a very common pattern in our programs: we have structs
representing the model, and structs representing **changes** to that
model, either in the form of requests or effects.

A superstructure will automatically define request and effect
structures, and can automatically construct them when you change
anything.

If we put all our above code in a superstructure:

> superstructure Poker {
>
> enum Suit { \... }
>
> struct Card { ... }
>
> struct Player { ... }
>
> root struct Game { ... }
>
> request fn bet(game: &Game, bettingPlayer: &Player, chipsToBet: int) {
> ... }
>
> }

Let's see what happens when we call the bet function:

> let poker = new Poker();
>
> (populate game with players and cards)
>
> poker.addRequestObserver({ println "Request: " + \_; });
>
> poker.addEffectObserver({ println "Effect: " + \_; });
>
> bet(&game, playerA, 7);

The above prints out:

> Request: Poker.BetRequest(1060, 7)
>
> Effect: Poker.Player.chips.AddEffect(1060, 7)
>
> Effect: Poker.Game.pot.AddEffect(1, -7)
>
> Effect: Poker.Game.whoseTurn.SetEffect(1, 1060)
>
> Effect: Poker.Game.lastBettingPlayer.SetEffect(1, 1060)

things to talk about next:

-   superstructures can consume effects and requests

-   effects are reversible/undoable

-   we can intercept requests, for a server/client sort of thing
