**So they\'re basically like borrow references but checked at run
time?**

Yep, you got it! It\'s nice because it\'s easier than borrow references,
and allows aliasing.

And, since 95% of them are elided at run-time and Vale\'s ownership
semantics give them perfect branch prediction, the run-time cost should
be negligible. If that\'s still too much, we can use region borrow
checking to opt-in to zero-cost references where we want (preferably
once we\'ve profiled and identified the hotspots!)

And then if that\'s not enough, one might also use bump-calling (and
maybe arena-calling if we decide to add that in), which give us
zero-cost references while keeping our safety.

For everywhere else, we stick with the easy approach for faster
development ;)
