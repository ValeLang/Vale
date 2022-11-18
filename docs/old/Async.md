we probably have to think about async transforms sooner rather than
later. luckily, it neednt be in superstructures (doublecheck that\...)

how do we do mut across async await?

probably just dont let it inline the box into the stack.

can we have something like producer graphs? what theyre really trying to
mimic is async await i think\...

can we make or, and, +, -, etc and in fact any function take promises?

isnt this what hyperlining is? but i want it outside ss!

what is a future but a stream which guarantees only one thing coming in

we should simplify agent, stream, futures, async, superstructure
together in some clever way.

after all, a superstructure is identical to an agent\... and all its
nodes\... hmm.
