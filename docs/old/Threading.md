we might need to send a cluster of mutables at the same time from one
thread to another, in case they\'re heavily interconnected. that way
debug mode can trace through them to make sure theyre a complete clique
and nothing outside references any of them.

for threads, tracer is only invoked on mutables when we send them across
thread boundaries.

thread tracers are given a pointer to a struct containing:

\- hash set of owning pointers

\- hash map of borrow pointers to int count.

\- hash set of immutable things

when we send something, we\'ll trace it to establish those maps.

checks:

\- borrows is a subset of ownings

\- ownings\' borrow count is equal to whats in that borrow count map.

it will increment the orca counts of all the immutables that we\'re
sending across.
