option A:

if we want to make a template only accept immutable params, then we
should prefix with #.

in fact, in general, if we have \# in front of something, that means
it's immutable, but it's probably only used in templating. we might
consider restricting its use to templates.

so, for mutable list, we'll see: List!:T

for immutable list, we'll see: List:#T

option B:

we can put requirements on the template.

List:(T; isImmutable(T))

option C:

in a template, a ! will signal that we [allow]{.underline} immutable,
but not that it [is]{.underline} immutable.

List:T only works for immutable.

List:!T says that it works for either.

templates might want to send things over the thread. they need to
restrict to immutable

templates might want to call a function then throw the result away (rely
on side effects). they need to restrict to mutable.

so, we need a way to have these 3 options:

\- restrict to mutable

\- restrict to immutable

\- be fine with either

option 1:

!T, T, #T

\# means immutable, ! means mutable, regular T means we dont care

option 2:

!!T, !T, T

gross.

option 3:

:(T; isImmutable(T))

if S is a subclass of B,

and i have a List:S, which is mutable, can I &lock it and know that
it\'s basically a List:B at that point?
