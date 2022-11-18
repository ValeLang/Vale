(Draft for the site\'s Ownership page)

(pull in explanations from
[[https://radon-lang.org/features/superstructures/references]{.underline}](https://radon-lang.org/features/superstructures/references)
as an intro, minus the context of superstructures)

Radon\'s memory management is fundamentally based on using **owning**
and **strong** references; every mutable object has one owning
reference, which controls its lifetime, and can have multiple strong
references. Like a foreign key constraint in SQL, the program halts if
we let go of the owning reference while there are still any strong
references to the object.

This page will explain some common emergent patterns in how we use
owning and strong references, and how they address the needs of those
looking for shared ownership and garbage collection.

**Shared Ownership**

The language does not provide a reference type for sharing ownership of
mutable data, like C++\'s std::shared_ptr, or references in any
tracing-garbage-collected language like Java or Javascript. There are
two reasons for this:

-   In most cases where people think they want shared ownership, there
    > are much better patterns, based on single ownership, to accomplish
    > the goal.

-   We can actually implement a much safer flavor of shared ownership by
    > using the above references.

Specifically, the patterns are:

-   **Clasp:** where two objects point at each other, and when one dies,
    > it will call a function on the other.

-   **Nulling Clasp:** a special case of Clasp where two simple
    > \"handle\" objects point at each other and each one would null the
    > other upon deletion.

-   **Broadcast:** where a \"handle\" object will register itself with
    > the pointee object, and when the pointee dies, will call a certain
    > function on all the handle objects.

-   **Weak:** a special case of Broadcast where the pointee\'s death
    > will null all the handle objects. This one is so commonly used
    > that it has been promoted to Radon\'s third reference type, beside
    > owning and strong.

-   **Pool:** where multiple external \"handle\" objects share *access*
    > to an object, but a central pool manages the *lifetime* of the
    > object. When there are no more handle objects, the pool can decide
    > if/when to destroy the object.

-   **Graph:** a special case of Pool where unreachable objects can be
    > detected.

**Clasp**

talk about:

-   it\'s the 1:1 version of broadcast

-   the two sides don\'t have to be the same class, they can call
    > different functions on each other

**Nulling Clasp**

talk about:

-   this should be people\'s first tool to reach for, and if they need
    > extra power, then regular Clasp.

**Broadcast**

talk about:

-   it\'s the many-to-one version of clasp

-   the observer pattern is basically this. when an observer interface
    > has a \"i\'m about to be deleted!\" method that wants all
    > observers to unregister themselves, theyre basically doing the
    > broadcast pattern.

**Weak**

talk about:

-   this is common in other languages

-   this isn\'t necessarily the best tool for the job, but it was
    > promoted to a third reference because it\'s at least an
    > *acceptable* approach which greatly reduces the barrier to entry
    > for people not yet comfortable with strong references

-   we lifted weak references\' implementation into a thread-local hash
    > map to make it a zero-cost abstraction

**Pool**

talk about:

-   c++ allocators are similar, and mention that Rust has
    > accidentally/clumsily pushed people into this pattern

-   this is better than Rc and std::shared_ptr because nothing can
    > outlive the central pool, and it\'s easy to find all outstanding
    > references

**Graph**

talk about:

-   just because it\'s the hammer that java uses for 100% of its
    > problems, doesn\'t mean that it\'s **that** useful. this is only
    > actually useful 5% of the time; cyclical references just aren\'t
    > that common, once youve tried all the other approaches. mention
    > common layering practice and how human thinking steers us away
    > from cycles anyway.

-   this is better than GC because:

    -   no objects can outlive the central pool

    -   collecting is on demand and deterministic

(perhaps also mention how a superstructure is a special case of Pool,
where things inside the pool can only point at other things inside the
pool)

(talk about how a C++ programmer should think of all this. perhaps even
mention that one can disable strong checks in production code, and also
the unsafe \"raw\" reference.)

(talk about how a Rust programmer should think of all this; their
indices are, basically, extremely awkward weak pointers, pointing at
objects in a pool. also talk about how when they do .unwrap(), it\'s
because Rust cannot express strong pointers, but that\'s what they
really wanted there most of the time)
