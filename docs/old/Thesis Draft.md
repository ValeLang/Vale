# Vale

# Goals

Vale is a new programming language that aims to address some drawbacks
of today's most popular languages. Vale has three broad goals:
simplicity, runtime speed, and memory safety. Most modern languages are
designed based on tradeoffs between these three closely intertwined
considerations. This section will discuss some of Vale's unique
approaches to achieving these goals, and why they may be preferred to
methods common in other languages.

## Simplicity

The first overarching goal of Vale is simplicity. The fastest languages
like C, C++, and Rust place some or all of the burden of memory
management on the programmer which can lead to complex syntax and force
programmers to use difficult patterns. When speed is paramount, this is
desired, but it makes certain problems difficult and can be intimidating
to beginners.

C has a pleasantly simple syntax, but the lack of automatic memory
management can introduce subtle bugs that are difficult to resolve. The
language prioritizes simplicity and in turn allows the programmer to
access any memory as long as it is not in use by the OS or other
processes. This allows some harmful bugs to go unnoticed until the
program halts due to an illegal memory access (a segmentation fault).
Tracing the origin of these bugs can be very difficult, even for
experienced C programmers.

Memory leaks are another common type of bug in C programs. These occur
when memory that is no longer in use is not released for reuse. Because
C does not do any automatic memory management, it is the duty of the
programmer to call free() on any previously allocated memory; neglecting
to do so results in memory leaks.

C++ adds some complexity to the C syntax to reduce the commonality of
memory leaks and other bugs related to memory management. Some examples
of this are the unique_ptr and shared_ptr keywords. A unique_ptr can be
the only reference to its object, then when the unique_ptr goes out of
scope and the object is no longer accessible, it will automatically be
freed. Similarly, multiple shared_ptrs can point to an object, then when
all the shared_ptrs are out of scope the object will be automatically
freed. Features like these are useful, but they still require the
programmer to understand memory management practices, and they add
complexity to the syntax. Vale aims to avoid this additional complexity
by automatically handling all memory management. More modern languages
with simple syntax like Python already do this, but Vale's goal is to
implement a faster automatic memory management technique (see RUNTIME
SPEED).

Vale provides a simple syntax that allows for most common functional and
object-oriented patterns. Additionally, Vale's Hybrid Generational
Memory (HGM) model (see Hybrid Generational Memory) reduces the workload
of the programmer with minimal slow-down at runtime.

## Runtime Speed

Another important goal of Vale is speed. Most languages that provide
automatic memory management sacrifice runtime speed to guarantee memory
safety during execution. Python, Java, and JavaScript programs tend to
execute more slowly than C or C++ programs because they require
additional computation to ensure memory safety (see MEMORY SAFETY for
more on how languages perform these computations).

## Memory Safety

The final and most important goal of Vale is complete memory safety.
Languages like Java, JavaScript, and Python have accomplished this task,
but at the cost of a significant reduction in runtime speed. Older
languages like C that put the burden of memory management on the
programmer are significantly faster than those that provide automatic
memory management. However, automatic memory management is often still
desirable because it makes programs easier to write and debug.
Therefore, two approaches to memory management have been dominant in
programming languages over the last few decades: garbage collection, and
reference counting. The persistent popularity of these two methods has
led to many variations and optimizations to improve runtime speeds.
However, these methods have some unavoidable drawbacks. Vale's HGM
utilizes an entirely new approach to memory management that addresses
some of these drawbacks and competes with the speeds of today's fastest
memory safe languages.

Garbage collection provides memory safety by periodically pausing
execution of a program to free memory that is no longer in use. This
approach is popular because it usually has better throughput than
reference counting which incurs constant overhead throughout execution.
Java and JavaScript both use garbage collection, but unfortunately not
all programs can afford these unpredictable pauses in execution. Python
uses both reference counting and garbage collection, so that the
programmer can opt out of garbage collection when they wish to avoid
these non-deterministic pauses, and opt in when they wish to reduce the
reference counting workload and improve runtime speed.

The other popular approach to memory management is reference counting.
At runtime, reference counted languages keep track of how many
references a program has to an object in memory, automatically freeing
the memory if the number of references drops to zero. While this is a
safe and consistent approach, it incurs much overhead because each time
a new reference is created or destroyed the language must access memory
to increment/decrement the reference count. While reference counted
languages have been heavily optimized, they still struggle to compete
with the speed of the fastest unsafe and garbage collected languages.

# Rust and the Borrow Checker

# Hybrid Generational Memory

Hybrid generational memory (HGM) is Vale's unique approach to memory
management that relies on Vale being compiled ahead of time and using
single ownership. Compiling programs before execution allows for static
typing which helps make Vale fast. Additionally it allows for static
analysis, analyzing the structure of the code before runtime to add
optimizations where possible.

-   single ownership: every object has exactly one owning reference

    -   when we create an object, we get its owning reference

    -   to get rid of an object, we must Destroy its owning reference.
        > compiler can insert these automatically

-   single ownership != borrow checker.

    -   borrow checker is an additional layer on top of single
        > ownership, specifically the \"mutability xor aliasability\"
        > rule, which makes single ownership memory safe.

        -   contrary to popular opinion, borrow checker indirectly
            > causes memory overhead.
            > [[https://vale.dev/blog/beyond-rust-innovations]{.underline}](https://vale.dev/blog/beyond-rust-innovations)

    -   vale instead uses HGM to ensure memory safety

-   single ownership lets us know when to free the object, at compile
    > time

    -   RC need an integer, increments, decrements, if-statements to
        > know at runtime

    -   GC needs to trace at runtime

more motivation:

-   borrow checker doesnt let us do a lot of safe patterns

    -   example: observer pattern

    -   [[https://github.com/yewstack/yew]{.underline}](https://github.com/yewstack/yew)

vale\'s goals

-   100% safety, like java/javascript

-   super fast, hopefully as fast as rust / c++

-   easy to use

how it does that:

-   ahead of time compiled

-   based on single owner

    -   have some examples of this

    -   talk about C

    -   mention C++, unique_ptr\<T\>

-   uses HGM

hgm is based on generational memory:

-   every reference has a \"target generation\"

-   every object has a \"current generation\" (managed by generational
    > malloc)

-   memberload, memberstore, ksaload, ksastore, usaload, usastore,
    > lockweak all do generation checks

quick comparisons of hgm with:

-   java

    -   100% safe

    -   nondeterministic pauses

-   rust

    -   not 100% safe

    -   complex, difficult learning curve

    -   restricts us to certain patterns/architectures (example observer
        > pattern)

HGM will address these

\-\-\-\-\-\-- end context, begin new stuff

hgm is a combination of:

-   generational memory

-   static analysis

    -   knownLive

-   scope tethering (maybe)

    -   keepAlive (maybe, part of scope tethering)

rust\'s complexity tradeoff:

-   [[https://blogs.dust3d.org/2019/03/13/why-i-rewrote-the-mesh-generator-of-dust3d-from-rust-to-cplusplus/]{.underline}](https://blogs.dust3d.org/2019/03/13/why-i-rewrote-the-mesh-generator-of-dust3d-from-rust-to-cplusplus/)

    -   \"The most beautiful thing about Rust is also a disadvantage.
        > When you implement an algorithm using C++, you can write it
        > down without one second of pause, but you can't do that in
        > Rust. As the compiler will stop you from borrow checking or
        > something unsafe again and again, you are being distracted
        > constantly by focusing on the language itself instead of the
        > problem you are solving. I know the friction is greater
        > because I am still a Rust learner, not a veteran, but I think
        > this experience stops a lot of new comers, speaking as someone
        > who already conquered the uncomfortable syntax of Rust, coming
        > from a C/C++ background.\"

-   [[https://news.ycombinator.com/item?id=23744577]{.underline}](https://news.ycombinator.com/item?id=23744577)

    -   \"in node.js turned into battles with dyn FnMut traits to pass
        > \"callback/event\" handlers around, spawning threads to not
        > block on libusb reading / WebSocket client reading, borrowing,
        > cloning, mutex locking, reference counting with Rust like you
        > can in JavaScript. You can\'t just \"move variables from one
        > scope into callback/lambda\" scope with any sort of ease in
        > Rust.\"

-   [[https://lobste.rs/s/jgcvev/why_not_rust]{.underline}](https://lobste.rs/s/jgcvev/why_not_rust)

    -   \"\> In Kotlin, you write class Foo(val bar: Bar), and proceed
        > with solving your business problem. In Rust, there are choices
        > to be made, some important enough to have dedicated syntax.

> I feel this pain every time I delve back in. Last time, I had to dig
> into Rc for something for the first time and it has some unexpected
> gotchas. I don't remember what they were now; this was 3 years ago.
>
> Perhaps I've been spoiled by using Ruby, Scala, and Java for most of
> my professional career. When I go look at Rust, I understand what's in
> use but I'm not sure why it's in use and how someone (re)learning
> might come across the correct tool. I love the Rust compiler's helpful
> warning messages; does it suggest the more advanced memory management
> things like Arc and Box now?\"

-   Rust\'s memory management takes up mental bandwidth

-   The \"why it\'s in use\" hints at the architectural damage point i
    > was making

```{=html}
<!-- -->
```
-   \"Refactoring is a massive pain! It's super hard to "test" different
    > data structures, especially when it comes to stuff involving
    > lifetimes. You have to basically rewrite everything. It doesn't
    > help that you can't have "placeholder" lifetimes, so when you try
    > removing a thing you gotta rewrite a bunch of code.

> The refactoring point is really important I think for people not super
> proficient in systems design. When you realize you gotta re-work your
> structure, especially when you have a bunch of pattern matching,
> you're giving yourself a lot of busywork. For me this is a very
> similar problem that other ADT-based languages (Haskell and the like)
> face. Sure, you're going to check all usages, but sometimes I just
> want to add a field without changing 3000 lines.\"

-   Rust makes people refactor more often, this is a symptom of how
    > conservative it is, and how it makes easy things hard/impossible

```{=html}
<!-- -->
```
-   [[https://www.reddit.com/r/rust/comments/i9sor7/frustrated_its_not_you_its_rust/g1ilbv0/]{.underline}](https://www.reddit.com/r/rust/comments/i9sor7/frustrated_its_not_you_its_rust/g1ilbv0/)

    -   \"\[In C++\] At every single step in this process, you start
        > with a simple thing where you understand everything, and it
        > adds one little thing. It starts simple and grows complicated.
        > C++ tutorials read the way Super Mario Bros plays. A thing is
        > introduced, you do a simple thing with the thing, then you do
        > complicated things with the thing. Rust tutorials read like
        > Dwarf Fortress. If you don\'t understand everything, you can
        > do nothing.\"

        -   IOW, C++ has opt-in complexity / gradual complexity, rust
            > has up-front complexity

-   [[https://www.reddit.com/r/rust/comments/iwij5i/blog_post_why_not_rust/g627nwx/]{.underline}](https://www.reddit.com/r/rust/comments/iwij5i/blog_post_why_not_rust/g627nwx/)

    -   \"I\'d add that prototyping and refactoring with Rust is quite
        > verbose and tedious, even if you\'ve learned the ropes. If I
        > want to try something out quickly, I often want to defer the
        > ownership issues until I am \"more ready\". In this way, Rust
        > front-loads tedious and repetitive work, whereas ideally I\'d
        > like to leave correctness for last when I\'m just fiddling
        > around. Say that 95% of the compiler warnings, during such
        > early stages, are not helping me find business logic bugs.
        > They\'re instead pointing to import-, ownership- and type
        > conversion issues that some other languages simply don\'t
        > bother with.\"

        -   rust has up-front complexity

-   [[https://www.reddit.com/r/gamedev/comments/jqv6qa/why_rust_programming_lang_is_the_future_of_game/gbpmklq/]{.underline}](https://www.reddit.com/r/gamedev/comments/jqv6qa/why_rust_programming_lang_is_the_future_of_game/gbpmklq/)

    -   \"I don't think games in the general case are a great use case
        > for Rust. A lot of the complexity in games comes from their
        > being lots of different subsystems that want to read and
        > modify values. Most of the Rust attempts I have seen to get
        > around this are become awkward to code with lots of arc usage.
        > Or they hide the shared data so the borrow checker can't see
        > what's going on (which breaks the advantage of using Rust).\"

        -   the indirect cost that the borrow checker incurs

-   [[https://news.ycombinator.com/item?id=26794281]{.underline}](https://news.ycombinator.com/item?id=26794281)

    -   \" Here I am on day three of an attempt to modify a rust program
        > with logic that would have taken about twenty minutes to
        > implement in C# or Java. \... I\'m really thrown into the
        > borrow-checker deep-end, but man writing rust feels like
        > working on one of those puzzles where you try to fit a set of
        > tiles inside a rectangle. You\'ll almost get it but then some
        > edge is sticking out. So you move the tiles around but this
        > leads to two edges sticking out now! So you do a whole bunch
        > of additional exploring before ending up right back where you
        > started with the one edge sticking out.\"

a common theme is \"it gets easier once you know the borrow checker\"
but really one just learns what the borrow checker rejects, and learns
to go to those hammers instead\... but they arent always the best
approach. this is also known as \"stockholme\'s syndrome\"

Vale can be complex, but all its complexity is opt-in; by default vale
code is simple, and if we want the extra 1% speed boost we can add inl
and regions.
