In Incendian Falls, when we were doing the game loop, we wanted to halt
game simulation temporarily while the unity layer did animations to
catch up. For example, if we had two enemies who both attacked, we want
to pause the world after enemy A attacks, wait for his animation to
finish, then proceed after enemy B attacks.

In Incendian Falls, we did inversion of control, storing the current
\"what we\'re doing\" in the metastate. Then it waited for the client to
call a resumeGame() superfunction.

A nice way to imagine what we\'re trying to do is:

> fn doAIForUnits() {
>
> foreach (unit in game.units) {
>
> unit.doAI();
>
> flushEvents();
>
> }
>
> }

flushEvents() would do a sanity check, and broadcast all effects
immediately. If it\'s a chronobase, it might even take a snapshot (or
maybe the client could take their own snapshot). It would then wait for
the outside world to call some sort of built-in resume() function.

Another way to think of this is to use async/await.

> async fn doAIForUnits() {
>
> foreach (unit in game.units) {
>
> unit.doAI();
>
> await flushEvents();
>
> }
>
> }

This would eventually return to the original superfunction, who would
probably store this operation in the metastate, and wait for a
resumeGame() superfunction.

Keep in mind, any approach we do will be scary for consistency. We need
the world to see a consistent view of the superstructure while it\'s
receiving events. For example, there can\'t be anything inside the
superstructure that\'s pointing outside the superstructure when we flush
events, or return from the intermediate superstructure. That\'s pretty
hard to do.

I think the async/await approach is the most flexible and usable. Until
then, inversion of control should be sufficient.
