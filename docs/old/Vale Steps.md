[[Uncategorized]{.underline}](#uncategorized)

[[Post-0.1 Documentation]{.underline}](#post-0.1-documentation)

[[Post-0.1 Better Errors]{.underline}](#post-0.1-better-errors)

[[Post-0.1 Rescuing]{.underline}](#post-0.1-rescuing)

[[Post-0.1 Renaming]{.underline}](#post-0.1-renaming)

[[Post-0.1 Finish Existing
Features]{.underline}](#post-0.1-finish-existing-features)

[[Post-0.1
Investments/Upgrades]{.underline}](#post-0.1-investmentsupgrades)

[[Post-0.1 Regions]{.underline}](#post-0.1-regions)

[[Compiler Speed]{.underline}](#compiler-speed)

[[Post-0.1 Speed]{.underline}](#post-0.1-speed)

[[Post-0.1 Niceness]{.underline}](#post-0.1-niceness)

[[Post-0.1 Blogging]{.underline}](#post-0.1-blogging)

[[Pre-0.2]{.underline}](#pre-0.2)

[[Post-0.2 Blogging]{.underline}](#post-0.2-blogging)

[[Distant Future]{.underline}](#distant-future)

[[Done]{.underline}](#done)

# Uncategorized

four pronged approach for now:

-   post the 0.2 articles.

-   prototype replayability, post those articles. can do this whenever,
    > but lets do it first.

-   write new articles: start by co-writing the Next Decade: GPU
    > article. can start whenever.

-   update existing articles, fill out iso+hgm+regions = speed, improve
    > comparisons. then, update everywhere that we link to the old ones,
    > and add forwardings. can start whenever.

make new versions of the old articles:

-   update comparisons to talk about java instead of JS. we\'re about as
    > safe as java.

-   rewrite gen refs article

    -   mention that we do just let it overflow. we do something else
        > for weak refs.

    -   mention that the vast majority of gen checks are elided by HGM,
        > own, iso, uni, and regions.

    -   mention that when we send to another thread, we recursively go
        > through, adding a certain number to all current generations
        > and target generations.

-   post a new HGM article, describing how it eliminates gen checks, or
    > rather, turns N gen checks into 1 scope tether. emphasize that it
    > detects UAF sooner, and is akin to
    > ConcurrentModificationException, and it can be opted out via
    > untethered, or via command line override.

-   post a new regions article, which mentions that shared mutability is
    > allowed within a region.

-   post an iso regions article, describing how they help eliminate gen
    > checks.

write an article on the general strategy of vale. then add a snippet to
the homepage

make a better rust comparisons page. talk about whats good about rust,
and then show how vale also has it.

-   gen indices -\> gen refs

-   refcell -\> scope tether

-   affine -\> iso

-   imm borrow -\> region borrow checker

-   compliment them then say how vale gets the same

-   goal is to get imm benefits wout strict mutability uniqueness

-   \"if you believe heap is always 100% evil, you might not understand
    > what vale is going for here.\"

-   \"if you believe run-time errors are always 100% evil, you might be
    > in some very specialized domains, and should be using Coq. also, i
    > encourage you to trace through your error paths and watch what
    > happens to them anyway.\"

prototype perfect replayability

prototype the region borrow checker.

-   start with imm and rw regions. might be the easiest. then we could
    > do some manual monomorphization to get some numbers with
    > BenchmarkRL.

-   dont do FFI yet. just disallow it.

    -   only a few kinds of regions: in a mutex, in an iso, the regular
        > region, the stack, maybe a side stack region like unsafe will
        > use. can we ever do a pure call, then FFI out, then call back
        > in, but with a reference to a region that\'s still mutable?
        > and is that okay? we\'ll probably want to scramble an iso\'s
        > region every time we close it, hmm. or maybe its fine, cuz we
        > can do the compare.

    -   what if a pure function takes e.g. a MyStruct\<\'r, \'x\>? i
        > guess in that case, we\'ll need to make immutable \'r, \'x,
        > and whatever the MyStruct is in.

make a way to test everything, and lets add some tests for windows. we
didnt catch the windows is_dir bug, which is telling.

solidify the release process:
[[https://github.com/ValeLang/Vale/issues?q=assignee%3AVerdagon+is%3Aopen]{.underline}](https://github.com/ValeLang/Vale/issues?q=assignee%3AVerdagon+is%3Aopen)

Make that lexer!

fix guide page terrain

next unblocked articles:

-   ~~New in Vale 0.2: Removing \"let\" and \"let mut\"~~

-   const generics article (just to r/vale)

-   concept functions (r/vale, r/pl, HN)

-   Vale Programming Language v0.2 Released

-   Next: Fearless FFI

-   Next: Perfect Replayability

-   Seamless Fearless Structured Concurrency, Part 2: Handling
    > Mutability

-   Next Decade in Languages, Part 1: Regions

-   New in Vale 0.2: Modules

-   Surprising Weak Reference Implementations Redux

breaking changes for 0.3 release:

\- add \^ and inlines

\- \[\] should be for lists and maps, not arrays and seqs

before next big article:

-   set up a weekly update part of the site

-   look into making a replit template or something for vale dev?

clean up midas:

-   see if we can stop using globalState-\>context and use that
    > GetGlobalContext function instead

-   see if we can replace some functionState appearances with
    > LLVMInsertBlock functions

-   see if we can get rid of some functionState appearances by just not
    > naming blocks

compile speed is actually good enough for small projects and
experimentation. lets have a branch where things are easier:

-   remove permissions and pointers

-   change stdlib to be compatible with it

and then really start pushing people to try vale. launch the article
spree, full-force. see what happens!

\- remove all exists, filter, etc.

\- if something\'s expensive, move it into another function, so the
profiler can pick it up

\- classify all functions by time complexity?

\- find all Map and List? and any times we add to a vector?

lets also short-circuit some common function calls like Opt.get/isEmpty
and math ops and maybe constructors.

18% spent in consumeWithCombinator

2% addPuzzle

3.7% spent in vectorbuilder. perhaps we should use reversed lists or
something more often? only for temporary things, then we assemble into a
vec later.

0.2% spent in OptimizedSolverState\'s constructor???

to get better data:

frame for RuneTypeSolver.solve

mark frame for expressionhammer translate

split solveRule into different methods

frame around all bodies of all lookupWithImpreciseNameInner overrides

thoughts for maybe a coming release:

-   fix TLTs\' ranges to include the attributes

-   rename range to Range, and the rest of the functions

-   Use sugar for array lists, not arrays.

-   finish translating parser, remove the weird distinction between rule
    > templex and pattern templex, and switch to scala-native

-   consider changing \# to @ or %, or if we get rid of pointers, \*

-   make it so we have to opt-in to scope tethering, and & just comes
    > out of those, and when we need to scope tether a yon, we have to
    > manually do it. will give us a better feel for the costs.

-   combine Vale_Examples, samples, and post-build integration tests.

    -   maybe figure out how to run the scala tests as part of the build
        > process

    -   run the midas tests before the build

    -   run stdlib tests and vale_examples (including roguelike.vale)
        > after unzipping

later, consider default arguments, to get the hash function from the
key\'s environment

Take HGM out of guide, and update gen refs article to say its hopeful.

Every reference to it should paint it as a hope, a goal, not something
we have.

The page on it in the vision should say its hopeful, we dont know if its
possible.

Add this to the generational references article:

> ! This article has been moved and updated! Check it out at \[Safety
> Part 1: Generational
> References\](vision/safety-1-generational-references).

Read:

-   [[https://elsman.com/mlkit/pdf/mlkit-4.3.0.pdf]{.underline}](https://elsman.com/mlkit/pdf/mlkit-4.3.0.pdf)

-   [[https://www.cs.tufts.edu/comp/150FP/archive/luc-maranget/jun08.pdf]{.underline}](https://www.cs.tufts.edu/comp/150FP/archive/luc-maranget/jun08.pdf)

-   [[http://joeduffyblog.com/2016/11/30/15-years-of-concurrency/]{.underline}](http://joeduffyblog.com/2016/11/30/15-years-of-concurrency/)

-   Dynami Region Inferen eDavid Pereira John Ay o kDepartment of
    > Computer S ien eUniversity of Calgary2500 University Drive
    > N.W.Calgary, Alb erta, Canada T2N 1N4fpereira\|ay o kg ps .u alg
    > ary .
    > [[http://citeseerx.ist.psu.edu/viewdoc/download;jsessionid=6856DA7B6C9D5CCE9BF892479B17E292?doi=10.1.1.132.1870&rep=rep1&type=pdf]{.underline}](http://citeseerx.ist.psu.edu/viewdoc/download;jsessionid=6856DA7B6C9D5CCE9BF892479B17E292?doi=10.1.1.132.1870&rep=rep1&type=pdf)

add sub-headers like
[[https://koka-lang.github.io/koka/doc/index.html]{.underline}](https://koka-lang.github.io/koka/doc/index.html)

-   make arrays use push and pop

-   take away absolute statements in the fearless page

Possible projects for someone:

-   Deterministic replayability

    -   Figure out imms\' future first

-   Make Valestrom multithreaded

-   Make LSP

    -   Need to figure out the basic way itll work

-   Adding poison type

    -   Need to figure out how it works in the inferer probably

[[Upcoming Changes
(Tentative)]{.underline}](https://docs.google.com/document/d/1p9RC6sK8UyD8PO81CgCJx5sTAGmjoo6ZhFlR0xvR60Q/edit#)

-   make it so valestrom outputs different definitions for every region,
    > then remove the hack in containsExtraStruct

solidify externs:

-   add resilient-assist

-   retire assist mode

-   encrypt mutable handles

make valestrom enforce structs need to be weakable to take a weak ref to
em

make a release-notes with user-breaking changes

solidifying:

-   Make benchmark program in vale, so we dont need 3 different ones

-   To release right now:

    -   On each: install things from scratch

    -   On mac:

        -   Run valestrom tests

        -   Run testvale on the site

    -   On each:

        -   Run midas tests (for all regions!)

        -   Run benchmarkRL

        -   Run roguelike.vale

Itd be nice to have on command that runs all this at once. A vale
script?

x str; // (1)

fix windows script, its making vstl a text file

fix the \"unnamed substruct\" example on the site and take off notest,
this doesnt seem to work.

b = Arr\<imm\>(5, {\_});

println(len(b)); // =\> 5

println(b\[0\]); // =\> 0

mut b\[0\] = 1; // \<\-- This should not be allowed ??

println(b\[0\]); // =\> 1

switch to PartialArrays instead of the generator stuff, because:

-   we can more easily read from the main args array into a string
    > partial array. then remove numMainArgs and getMainArg. would this
    > interfere with safety?

-   we can have an efficient vector type.

should we also/instead have a native expandable vector? or leave that to
codespace?

~~categorize all these tasks, figure out what others can work on~~

~~re-triage the later tasks, make sure we didn\'t miss anything critical
for 0.1~~

-   read cyclone paper

To count lines:

find . \\( -name \"\*.vale\" -o -name \"\*.scala\" -o -name \"\*.h\" -o
-name \"\*.cpp\" -o -name \"\*.java\" -o -name \"\*.c\" -o -name
\"\*.py\" -o -name \"\*.rs\" \\) \| grep -v target \| grep -v out \|
grep -v release \| grep -v cmake \| grep -v test_build \| grep -v
BuiltValeCompiler \| grep -v CMakeFiles \| grep -v \'/build\' \| grep -v
subprocess \| xargs wc -l

-   ~~post surprising weak ref implementations~~

-   post fearless FFI article

-   post beyond rust

# Post-0.1 Documentation

rename mutable and immutable structs to instance and value structs.

site:

-   add a note to raii-next-steps: when comparing with rust, keep in
    > mind that rust\'s Rc is intrusive, not a fat pointer.

Site:

-   Benchmarks

-   Reference: Optional ?

```{=html}
<!-- -->
```
-   make \<Note\>s appear after their paragraph, and with a special
    > style, maybe even have a click to expand via css.

    -   means no-js users can look at it that way

    -   mobile users can look at notes inline.

```{=html}
<!-- -->
```
-   Examples have equivalents in other languages

```{=html}
<!-- -->
```
-   Have translations for java C and python for each snippet

```{=html}
<!-- -->
```
-   make team page

-   Reference:

    -   Fill out regions a bit more

        -   mention bump pointers

        -   mention native calling

        -   mention safecalling

        -   Talk about how we can customize the mode per region holy
            > shit. or maybe just override? also, maybe we can turn
            > crefs into unowned refs on a per-module basis or even for
            > a specific field. all this would be thru a config file.

    -   define raw pointer

Intro page:

-   Add the downloads link back in (commented out)

-   Add a Syntax Design for Varying post, and un-comment the link from
    > the intro page

-   have a side note for type inference

-   Move entire tuple-vs-array thing into the side note

Update site:

-   rewrite docs to use the new site markdown language

Add some installation steps for site to site readme:

-   sudo snap install google-cloud-sdk \--classic

Add instructions somewhere for setting up intellij

-   Need to use java 11 sdk cuz thats the latest google cloud functions
    > supports

add
[[Troubleshooting]{.underline}](https://docs.google.com/document/d/15BzndHrphdpocDKuf4PAnu4rZwQrzuYEsamVu7MydCc/edit)
to repo

-   and details about extern and calling into C

-   add some details about our STL to the vale site

-   update the regions page in the reference, it\'s missing a ton

-   write a guiding principles and goals page, to reference when someone
    > tries to make vale something its not

-   update reference to talk about how we can be nonatomic because
    > thread isolation

# Post-0.1 Better Errors

assist mode backreferences, for cref err msgs

main() int {

hello = \"ab\";

a = hello.slice(0,1);

b = hello.slice(1,2);

println(a.toAscii()); // \"97\"

println(b.toAscii()); // \"98\"

println((a + b)); // \"ab\"

println(a.toAscii() + b.toAscii()); // \"195\"

// notAmethod(); // humanized error message

//println(int(a.toAscii()) + int(b.toAscii())); // NoSuchMethodError
exception

//println((int(a) + int(b)).toAscii()); // NoSuchMethodError exception

//println((int(a) + int(b))); // NoSuchMethodError exception

//println((a + b).toAscii()); // NoSuchMethodError exception

ret 0;

}

make it so you cant drop a c-ref

src/main.vale:76:23: Couldn\'t find member begin!

print out the struct too

# Post-0.1 Rescuing

add test for \"Can\'t extend a struct\"

break up globalstate

add test for \"Interface method must have a virtual parameter!\" error

take out the ph2 = ph; in Markvale\'s print.vale, it seems to have
trouble with double closures

~~add test for MMEDT~~

right now when we receive an owning reference from the outside world in
generational memory, we do a generation check. make sure the thing is
still alive.

actually wait no, we need better than that. we cant have two owning
references flying around. we\'ll need some sort of bit or map to say
whether its owned by the inside or outside.

add test for that

while determining what functions are actually available i tried added
vstl/strings.vale to the command line, which includes it twice. when i
use a slice, it then blows up with a spectacular error

this crashes:

{

hello = \"hi world\";

print(hello\[0\]);

ret 0;

}

\"Simple ArrayList, no optionals\" moves an outer var from inside a
lambda, disallow that?

when we ret panic(\"blah\") the ret tries to unstackify things,
resulting in instructions after the never in midas. prevent it somehow?

add tests for all the compile errors

add test for a panic inside an if else ladder where all branches return

add test for vstr_substring

maybe make some way to generate/update the VS and idea and sbt projects?

\"Bail early, even though builder is still pointing at the preceding
block. Nobody should use it, since nothing can happen after a never.\"
\<- make sure this is enforced, that valestrom never gives us anything
after a ret instruction or an instruction returning a never

get rid of ExternFunctionTemplata

make it so we can only hand borrow refs, not shared refs, to ===

-   Run all vivem integration tests against midas

-   take out the weird thing where & makes another rune. just make it
    > apply to coords!

-   fix the five failing tests

```{=html}
<!-- -->
```
-   IFunctionGenerator: TODO: Give a trait with a reduced API.

-   reconcile convertHelper and TemplataTemplarInner\'s
    > isTypeConvertible/getTypeDistance/isTypeTriviallyConvertible

make externs in midas a bit better. right now theyre tacked onto the end
of main and in function.cpp.

make it so var counters are in the function\'s environment, not the
blocks\' environment. separate the two perhaps?

-   Templar should say when we\'re using an already moved variable

-   in midas we insert into structReferends twice, as a hack. look for
    > &cache-\>structReferends.

-   Scout should say when we\'re using an undeclared variable

-   divide test for midas

-   revisit every param in every IRegion method, reconsider which side
    > of the boundary it should be on

-   simplify func names in hashset.vale to be the same as hashmap, see
    > the errors it causes in benchmarkrl. we need a good error for
    > that, cuz that was tricky.

-   when we include a file twice we get weird collisions

refactor midas:

-   nail down what it means to load from an inl for mut. surely we dont
    > load the entire struct into a register\...

-   put inline boxes back in, we took them out in hackathon

```{=html}
<!-- -->
```
-   unknown size array wrapper structs have:

    -   control block

        -   length

        -   contents

    -   it caused a bug. lets make it

        -   control block

        -   something

            -   length

            -   contents

make genheap not read the size of the object from the object. let the
region itself do that.

-   most regions can know what the size is when they deallocate

-   RC can put it in the control block, so we can free interfaces, OR rc
    > can just not use gen heap.

-   fancy new malloc can put it in the address\'s bits 10-15. not sure
    > if needed.

in every edge, put a pointer to the interface, for the census, so it can
doublecheck that its pointing to the *right* edge.

clean up this tech debt:

-   ~~change these return nevers to return voids~~

-   huh, hammer translates void to Tup0, maybe dont even have void in
    > valestrom at all?

-   perhaps make Tup0 translate to void when in return type, so we can
    > be compat with C

-   put in validation that we\'re accessing the right fullname from a
    > memberload/store, that it actually matches the index we\'re asking
    > for. had a misalignment for boxes.

-   make valestrom output the locals for each function, so we dont have
    > to insert them in midas like we do (and valejs)

-   get rid of BFunctionA

-   instead of making RefLE directly, have something that does a
    > translateType check before making it.

-   get rid of Function2.variables, it doesnt contain all of the
    > variables in the function, just the ones at the top level, nothing
    > inside any blocks

-   make WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI etc use something like
    > ControlBlockLayout

-   

-   put a census check in wrap()

-   make it so our typenamestr, objid, usalen are all inside the
    > references themselves. that way if we have a weak ptr to something
    > we can know what it was easier

-   make a way to browse the heap, or dump it to a file or something

-   make some sort of mode that prints out what things we put into a
    > struct, and what things we mutate into a struct

    -   make fillInnerStruct nicely print out things, theres an impl in
        > there

Finish refactoring Midas

-   Remove all temporary methods from IRegion, look for \"TODO Get rid
    > of these once refactor is done\"

-   Get rid of as much of GlobalState as possible

-   Get rid of the entire shared directory so we can be sure there isnt
    > any region-specific stuff isnt in there

-   Reconcile this withUpgrade/withoutUpgrade stuff. gotta also think
    > how it works with locals.

-   make sure we dont match comments in strings

-   clean up blogify.py to have helper functions or lambdas or something

```{=html}
<!-- -->
```
-   add tests:

    -   WrongNumberOfArguments

    -   WrongNumberOfTemplateArguments

    -   SpecificParamDoesntMatch

    -   SpecificParamVirtualityDoesntMatch

    -   Outscored

    -   InferFailure

    -   returning something other than int or void from main should give
        > nice error

\- change net.verdagon.vale to dev.vale (finally)

\- make a better error message for when the directory (as specified by
-op) doesnt exist

\- bring this parser output thing into valec.py so we dont have to run a
jar directly

Take -lm out

re-enable externtupleparam test. perhaps wrap known size arrays in
structs, for compatibility with C?

re-enable neverif test

add tests:

-   weak refs to mutusa, mutksa

add tests for all the strings.vale functions

-   // in the middle of a string didnt work

    -   workaround: escape the slashes

go through all the vfails in valestrom and make them into errors

reorganize scripts and everything around midas

for markvale:

-   fix ksa/tuple disagreement so splitAt can work:

    -   make it so we can have a \[int, int\] tuple without it becoming
        > a KSA. otherwise, when we hand \[4, 5\] into a function
        > expecting a tuple, things wont work.

    -   alternatively, make it so \[int, int\] cant be a tuple, its
        > instead a ksa.

    -   maybe have TupleT2 consume both understruct or underksa?

-   maybe have a ksa\<N, T\> function that makes a known size array of
    > that size? maybe CSArray? then RSArray can be runtime-sized
    > array\... or can we combine tuple and ksa into a \"sequence\"
    > type? and if we could even shove it down into hammer, that would
    > be nice\...

fix the templateArgs.map(\_.toString.mkString) we left in
TemplarErrorHumanizer

# Post-0.1 Renaming

-   rename all Borrow to Constraint

-   rename referenceRegister

# Post-0.1 Finish Existing Features

~~isa~~

struct Moo impl IMoo { }

-   improve borrow refs so they die on the last use

error for: fn main() { x = 4; = x.begin; }

figure out how to make a custom constructor with the same fields, for
example to do some asserts for StrSlice. right now its colliding with
the default one we supply. perhaps some sort of
#\[derive(!constructor)\] thing? what would be consistent with
sugar-not-magic?

figure out the real difference between unsafe-fast and resilientv3

# Post-0.1 Investments/Upgrades

the serialize functions return an inline linear struct, but it doesnt
make much sense. would make more sense to have an rcimm struct which
points into the linear region. someday when we have regions in coords,
we can do that.

take out all these redundant parameters in VAST and midas

make valestrom put the mutability and region in every referend so we
dont have to look them up

Investments:

-   Make IntegrationTests better, make it so everything in there becomes
    > either a unit test, or is an integration test shared with Midas

-   Make IntegrationTests read the expected output from file comments or
    > a neighbor file

-   Merge midas integration tests into valestrom\'s

```{=html}
<!-- -->
```
-   attributes should only be used as a passthrough mechanism to a stage
    > that actually cares about it. anything a stage cares about should
    > be a field. make sure this is true!

-   move VonHammer outside of hammer, maybe its own thing

-   rename ProgramH etc to ProgramM

split up compileValeCode

improvements:

-   naive rc DOES NOT WORK. it conceptually decouples destruction from
    > freeing, but doesnt have the destructors to pull that off! it
    > should only be used for benchmarking.

-   make it so we dont link in stdlib functions we dont use, like the
    > various genMalloc/Free things

-   assist mode decrements the RC when we free, can we take that out?

-   make better unique names, that still include template args and stuff

put a \_0v \_1v etc at the end of all vale functions. we defined an
assert in vale, and it collided with the builtin assert.

make the larger tests into postsubmits somehow?

stop using insertvalue for control blocks

rename templex. genex? compiletimex? cotex?

LambdaReturnDoesntMatchInterfaceConstructor shouldnt be a thing, arent
open interface constructors just syntactic sugar basically?

-   Exception in thread \"main\" java.lang.RuntimeException: Need return
    > type for
    > Signature2(FullName2(List(),FunctionName2(writeJson,List(),List(Coord(Borrow,StructRef2(FullName2(List(),CitizenName2(NameP,List())))))))),
    > cycle found

    -   have a better error message that explains wtf is going on here,
        > perhaps shows the series of events leading to this

# Post-0.1 Regions

Right now we do serializing in serialize.cpp and its kind of
interesting. We manually store all fields into the destination.

Instead, we should have IRegionArchetype and IRegionInstance

# Compiler Speed

we\'re making wrapper functions for interface functions, which is fine,
but we\'re probably also emitting LLVM for them.

When midas is adding its extra methods (like for serializing
immutables), it does some linear searches, perhaps add some indexing.

Midas has a separate method for calculateSerializedSize entry in
extraFunctions for int, bool, float, and *every single borrow ref*. lets
not do that.

# Post-0.1 Speed

Midas\' wrc weaks implementation takes in a pointer to the wrc table.
might be better to have those be globals again, so we dont have that
extra load.

-   Change from having return values to assigning into a first-param
    > pointer, so we can use the same function to return inl as non-inl.
    > maybe just constructors, maybe more.

-   Make a benchmark, of something contrived. perhaps A\* through an
    > irregular polygon graph. lock main region, can have pointers into
    > them at will. itd be pretty cool.

-   Bounds check currently takes a signed int, maybe cast to unsigned or
    > only take unsigned?

-   Benchmark: mandelbrot from
    > [[benchmarksgame]{.underline}](https://benchmarksgame-team.pages.debian.net/benchmarksgame/fastest/gpp-rust.html)

make a way to join strings efficiently

BenchmarkRL

1.  ~~make the roguelike in rust~~

2.  implement in vale.

3.  ~~count RCs. maybe find a way to count cold ones? hmm\...~~

4.  ~~run in fast mode, benchmark.~~

5.  ~~add resilient mode~~.

6.  start doing optimizations, see below list.

7.  then add bump calling and pool calling, compare again, with
    > resilience off. these will be raw ptrs. thats vale fast mode with
    > bump/pool calling. can just be a call out to a C malloc hook?
    > using struct typeid

8.  then add a \"frozen ptr\" to kind of mimic implicit locking. or
    > dont, and just use raw const ptrs. resilience on, should be super
    > fast.

9.  count RCs

10. take the difference with and without the incr/decr, and apply 5% of
    > it, to estimate lobster improvement

11. do same for num RCs

12. make sure to nuke the cache every turn.

13. dont use stdfunction. its atomic. make interfaces manually. or maybe
    > do use stdfunction?

differences still:

-   vale compiler could theoretically optimize implicit locking a tiny
    > bit faster, maybe it can hint llvm that its deeply immutable.
    > restrict perhaps?

-   c++ vptrs are in the obj. can maybe use dyno? or tiny fatptrish
    > classes?

rust may win in initial generation but just have more turns. find the
tipping point.

optimization steps:

-   make Vec a smart vector, where it can store a few things inside
    > itself!

-   add inl.

-   add automatic inlining

-   add generational weak table

-   add generational heap

-   add primitive region support, perhaps just an integer for now.
    > 0=normal, 1=pool, 2=fast

-   add pooling using region=1, and a method to call to nuke the pool

-   add the chunk table for the pool, to make \"pointers\" 32b. perhaps
    > 20b chunk index, 12b offset?

-   add unsafe pointers, using region=2

-   add a way to inline returned options.

-   measure cache misses with perf

-   implicit locking, to cut down on ref counts

-   pool calling, cut down on ref counts again. use it for terrain
    > generation and AI.

-   maybe use persistent pools for units and tiles, maybe even purge
    > once in a while?

-   VGWT (generational weak table)

-   lobster algorithm

-   inlining mutables.

    -   do we fix interior pointers? i think so, but problem for later.
        > interestingly enough, things can point outside.

    -   would prefer to not have to use this. so many awkward problems
        > arise when dealing with inlining\...

-   fast mode

-   fast resilient mode, using VGH

# Post-0.1 Niceness

stdlib:

-   make Arr into Array, and make it assume mutable. add an ImmArray
    > function that does immutable, then a BaseArray that takes the
    > param.

-   adjust syntax: each myList as i { \... }

-   finish in patterns page the: mat inputInt

    -   and the mat serenity

make it so we can just say vale instead of python3.8 valec.py

add:

ret;

override \_\_index function

# Post-0.1 Blogging

[[Article
Plan]{.underline}](https://docs.google.com/document/d/1sRS_Kv1Ra94gliE8EapAT8exJCJ_RmAghfUGI6PNalU/edit)

# Pre-0.2

-   \[\] for array indexing, if theres an identifier right before it

-   change \[\<imm\> 2 \* int\] to \<imm\>\[2 \* int\]

-   maybe find a way to coerce an immksa(int) into a mutksa(int)?

```{=html}
<!-- -->
```
-   make it easy for people to use:

    -   make a playground with scala-js?

    -   build with scala native and make Midas call into it

    -   self host LOL

-   Match statement

    -   Will need the checkingtranslate

if-let

-   make it so we can make a tuple mutable or immutable at will? or not.
    > hmm.

-   make it so main has to have an export

-   detect statements like \"i != 0;\"

-   make int 32 bits by default, because of javascript and cache
    > efficiency.

```{=html}
<!-- -->
```
-   reconsider all keywords. why do we have \"abstract\"?

make scout readable to outsiders

-   rename to beginInclusive and endExclusive

-   Fix compiling without a main function

Fix this stl situation, this is a mess. probably put it all inside
Midas, so we can just run things from there.

invocation/environment fixes:

-   Make it run the .bat file automatically like rustc does

consider enum syntax:
[[Enums]{.underline}](https://docs.google.com/document/d/1HUUxWJZZcRBR2LyDqihNy1Tc8OOHP295WrnRh2MpRJg/edit#)

-   things are awkward when we dont supply any input files

    -   1\. i could make it say \"no input files found, try a command
        > like: (command here) helloworld.vale\"

    -   2\. start a repl session =D

    -   3\. hide the stack trace and Running: line

make assertions report file and line theyre on

# Post-0.2 Blogging

once compiler is fast, and things are pleasant:

-   post on what life is like with UFCS. what to do when you have the
    > choice between \"moo\".slice() and slice(\"moo\")

-   **PL Post** Why arent we just using spaces around \< and \> and
    > other bin ops?

-   **PL Post** UFCS in Vale

-   **PL Post** const generics in vale. not actually hard!

-   

-   **PL Post** lambdas for making arrays, and also the goose chase of
    > generics, lambdas, and interfaces it set me on.

comparisons with other langs:

-   An article on moving global state into interface methods

-   an article about what we\'re improving on rust:

    -   \"What Rust can Teach to New Languages\"

    -   \"Reducing Rust\'s Complexity\"

        -   \"Let\'s start by acknowledging: Rust\'s made very wise
            > tradeoffs for its intended use case. Rust shines in
            > embedded programming, where we need absolute control over
            > every allocation. \... That said, there are other
            > situations where the tradeoff doesn\'t work as well, such
            > as web development.\"

        -   (perhaps link to the article on moving global state into
            > interface methods)

once we have inlining:

-   comparing memory models:

    -   HGM vs RC vs GC

        -   could make a post comparing HGM to RC to GC. can use valeJS
            > to measure GC.

        -   then say that the next step would be using JVM (volunteers
            > plz) and rust. we suspect rust will be the fastest, and
            > our goal is to be as fast as rust.

        -   maybe also mention 100% safe, which is why we compare to RC
            > and GC.

        -   how is hgm better than a hypothetical lobster+final refs?

        -   seems like we do the same overhead; they incr+decr, we do
            > scope tethering.

        -   ah, we only do it when we think its necessary.

        -   also, we can have inline things.

        -   we should totally write an article on this. we have the
            > strengths of rc \*and\* memory control.

        -   we should also talk about scope tethering on arrays, which
            > mean we can have knownlive refs to the arrays members.
            > like a boss.

    -   blog about memory safety schemes:

        -   keep the object alive with GC

        -   keep it alive with RC

        -   detect if its dead with another RC

        -   detect if its dead with a generational index

        -   crash if any pointers still pointing at it with RC

        -   use pool allocation so we only reuse things of the same type

        -   vale uses those last three, as part of its very foundations.

once we have regions:

once we have hgm:

-   put this in an article: \"combining the benefits of rust and RC\"
    > that talks about GM

    -   rust array bounds check:

        -   \- vecptrptr = load from arg

        -   \- vecptr = load vecptrptr

        -   \- sizeptr = vecptr + 8

        -   \- size = load sizeptr

        -   \- inbounds = index \< size

        -   \- if !inbounds \...

        -   \- elemsptrptr = vecptrptr + 0 (no op)

        -   \- elemsptr = load elemptrptr

        -   \- elemptr = elemsptr + sizeof + index (2 op)

    -   9 instructions for rust, lol

-   article on things manually done in rust, automatically in vale:

    -   \- pools. gotta do one for each type. vale just has an entire
        > pool region!

    -   \- arenas. vale just annotates it. also, can fit existing
        > functions to use an arena!

    -   \- immutable borrows for speed. vale does it on an entire region
        > basis!

    -   \- rc,refcell to make sure nobody modifies / changes the shape
        > of an object: vale has scope tethering and uni inl sealed
        > interfaces for that!

    -   \- in rust we use arrays for things we expect to never go away.
        > on vale we can use arena

    -   \- in rust we use gen indices for long running statefulness. in
        > vale we use hgm

-   \"vale for rustaceans\" article. talk about:

    -   \- rust has runtime overhead. compare to hgm

    -   \- borrow checker is like hgm static analysis

    -   \- rust traits are crippled, polluting trait method parameters

    -   \- rust OO is crippled, see observers

    -   \- match enum is virtual dispatch

-   an article named \"quantifying rust\'s run-time safety overhead\"
    > lol

-   \"An Easier Rust\"

    -   Talk about how we\'re automatically handling all the hard parts:

        -   we have generations for you

        -   we have bump regions for you

        -   we use goroutines instead of async/await

        -   region borrow checker instead of object borrow checker

        -   no refcell and borrowing! scope tethering and static
            > analysis instead.

        -   faster compile times!

-   **Where does (safe) Rust\'s speed come from?**

    -   we\'ll link to this article when people complain about \"it
        > doesnt have a borrow checker\"

    -   forcing data-oriented design, which sometimes backfires (link to
        > EC\>ECS)

    -   making heap allocations explicit

    -   using generational indices

    -   single-threaded RC

    -   all at a big cost: the borrow checker\'s difficulty.

once we have probabilistic hgm:

Whenever:

-   article on runtime and compile time polymorphism being the same
    > thing, via type erasure

-   

-   an article on why vale can do this kind of structured concurrency
    > reading when rust cant: vale has no escape hatches (it needs none)

-   an article about dynamic dispatch in rust: enums, interfaces, router
    > functions for enums

-   

-   blog post on why rust is a puzzle and that feels good but thats not
    > what we want in a language. gc is nice because we dont want mental
    > overhead.

-   blog post of \"why vale didnt build on top of rust\"

-   write blog post about how you need the right tool for the area.

    -   https://news.ycombinator.com/item?id=24293046

    -   zig for no alloc things like this, rust for embedded drivers
        > maybe, c++ for AAA games, vale for servers and games and app
        > cores, GC for short lived processes maybe\...

-   

-   Read robert\'s
    > [[series]{.underline}](https://blog.polybdenum.com/2020/07/25/subtype-inference-by-example-part-4-the-typechecker-core.html),
    > we might want to use his system for our type inference

-   post regions again when we post weak refs article

-   **PL Post** [[Vale: The Interesting
    > Parts]{.underline}](https://docs.google.com/document/d/1t0zzW0K9jilbCkuAulZfDlZcYioRO10Ny5K7LEciyMI/edit#heading=h.ppyeicmu9b4s)
    > Put cross-compiling back in.

-   **Wide Post** Shortcalling syntax added to Vale

-   

-   **PL Post** ar Types. explain the use to non cpp devs

-   [[Posts about
    > Rust]{.underline}](https://docs.google.com/document/d/1OvAnu2VSWynN4KK71F2UZuvGsa1I11tKQTCZOnD8iBA/edit)

-   post on reddit: why not i32 by default (benchmark first) and talk
    > about xcompile to JS

    -   looks hopeful:
        > [[https://stackoverflow.com/questions/163254/on-32-bit-cpus-is-an-integer-type-more-efficient-than-a-short-type]{.underline}](https://stackoverflow.com/questions/163254/on-32-bit-cpus-is-an-integer-type-more-efficient-than-a-short-type)

-   pragmatism in langs

    -   start with uuids

    -   talk about HGM and virtual memory and generation max

    -   talk about the probabilistic hgm

    -   \"pragmatic past the death of the universe\"

    -   or we could just write a compactor that kicks in on malloc
        > exhausted after a million years

**Wide Post:** [[Zero-Cost References with Regions in
Vale]{.underline}](https://docs.google.com/document/d/1DgOZ_gjIwe23L-P07P1I6kvTWUj9x8RiuOxQdIDRtFs/edit#)

**Wide Post** The Vale Programming Language - Version 0.1 Released! (w
benchmark w weak ptrs? an RL or asteroids?) or \"Vale Programming
Language: Speed and Safety with Ease\"

**Wide Post** [[Fast Cross-Platform
Code]{.underline}](https://docs.google.com/document/d/1c__owHS9BOqb67dbEjHOr_Dql-8sWiYiqGWDU0PEwTE/edit#)

maybe call it Universal Cross-Compiling?

must resolve tension between this and extern calling into C.

**Wide Post**
[[Modes]{.underline}](https://docs.google.com/document/d/1J-VQxpo202GXz500Awd4Eu0SSWczKl2WcdDsG0xf8xE/edit)

**Wide Post** Safety and speed: Vale, Rust, Swift, and Go (w benchmarks)

**Wide Post** 2x Speedup with Vale Regions

**Wide Post** [[Single Ownership is the
Key]{.underline}](https://docs.google.com/document/d/140EjhxP0F7HwQQPDgp2R5rY_AYK35HdYX9p3cZm605U/edit)

**Wide Post** safecalling, with readonly and rollbacks

**Wide Post** Fast Pentagonal Terrain in Unity, written in Vale

**Wide Post** [[Safety, Speed, Simplicity: Comparison of Vale, C++, and
Rust]{.underline}](https://docs.google.com/document/d/1s133QIYGlwfAunTxsz7HHPdUOjFK8tmCSCv2rn_k3UU/edit)

**Wide Post** [[Regions and Type Aware Pools For Zero-Cost Memory
Safety]{.underline}](https://docs.google.com/document/d/1g8ApToq7fRuhJA1UTJG1_BqCyRR877QUKQtX0hWcu7Y/edit)

Post some less interesting topics:

-   Mutate destroys member after moving it out of the object

    -   maybe put this in a post about \"things about C++ that vale
        > taught me\"

-   Auto-inlining

-   Shortcalling

-   UFCS

-   Destructors

-   Const Generics and Metaprogramming in Vale

-   Propagating Errors Past Destructors in Vale

-   Variant Indexing in Vale

-   Patterns with Single Ownership

-   Error handling

-   Structural subtyping with open interface constructor shortcalling

-   talk about receiving owning interfaces?

-   Single ownership (clasps, etc)

maybe an article for nonPLers which says \"once you know the warts of a
language, you can design something that works around just that wart.
examples:

-   first arg is unique ptr to this

-   inheritance is just the three things. just more typing.

-   borrow checker checking against safe things. Rc is good.

-   exceptions in c++ are just an implicit return type. with that in
    > mind, improved raii becomes easy.

```{=html}
<!-- -->
```
-   Blog: Safety without Garbage Collection

-   blog post on pemtagonal terrain generation in vale

-   Blog: Constraint and Borrow References

-   Blog: Patterns with Single Ownership

-   Blog: shortcalling

    -   talk about how the one downside might be the compile times.

-   Blog: crefs are the solution to cycles and rc in general. maybe even
    > say that crefs are a way to notice cycle problems early

-   Blog: Conviction. single ownership, no nulls, \`this\` is not
    > special.

-   Blog: Unity!

-   Blog: Wasm!

-   Blog: mut will swap something out. also that C++ destructors are
    > risky.

-   Blog: Safecalling

    -   Safecalling something means getting an Opt\<T\> out of it (or a
        > Result perhaps). Will be None if something inside panicked.
        > Inside a safecall, all mutable effects on the outside world
        > will be recorded, and rolled back on panic. If you safecall a
        > pure function, then it\'s quite fast. Perhaps could use the
        > \"try\" keyword?

-   regions post

    -   possible titles

        -   \"Faster Memory Management with Regions\"

        -   \"Even Faster Memory Management with Regions\"

        -   \"Zero-Cost References with Regions\"

        -   ~~\"Reducing RC Overhead with Regions\"~~

        -   ~~\"Free Ref-counting with Regions\"~~

        -   ~~\"Eliminating RC Overhead with Regions\"~~

    -   maybe at the end, talk about how lobster is making great strides

    -   then end it with \"with these four innovations, we think
        > reference counting will be the clear choice for new
        > languages\"

    -   remember to link to basil\'s post.

    -   ironically, single ownership made it easier to secede, which
        > made regions easier, which reduced rc cost, which made crefs
        > cheaper.

-   x compile post:

    -   \"multi-platform\" instead of cross compile? maybe

-   Cross-compile post:

    -   talk about how vale could be the new bridge between c++ and
        > these other langs. maybe even with a malloc hook?

# Distant Future

-   use RAII to make sure that child environments\' unstackified locals
    > get incorporated into parent environment, or at least not
    > forgotten.

```{=html}
<!-- -->
```
-   Email Adam: adam.dingle@mff.cuni.cz

```{=html}
<!-- -->
```
-   consider not needing semicolons

optimize ParseErrorHumanizer

-   make sure to optimize all the stl functions we\'re adding, like
    > signum

-   allow , at the end of argument lists

```{=html}
<!-- -->
```
-   make it so we could print out a whole array

-   do shortcalling syntax for simple structs? or better yet, do it for
    > everything but dont use the num params as a hint. resolve the
    > overload usimg the other params! then post about it.

bitwise funcs: xor, or, and, left shift, right shift, (sign extending
true or false), rotate

add a i8, u8, u16, i16, etc

reconsider having inner structs at all. the only reason we\'ve ever had
them in c++ or java is for private protected etc, but cant we just use a
filepriv? or modules/namespaces?

break, continue

get resilient-limit closer to unsafe-fast. figure out what the
difference is

finish benchmarking:

-   all these are only using objects on the heap, not the stack, which
    > is probably a big slowdown

-   resilient-v2 and -v3 could get faster or slower when we add the
    > stack, its uncertain

-   all of these are using the generational heap, which is still
    > slightly slower than the regular malloc (this will be fixed when
    > we fork mimalloc or jemalloc)

-   unsafe-fast is probably not actually equivalent to C++ yet, C++
    > probably has a ton of optimizations we dont yet, that i dont know
    > about

-   we\'re using a List and HashMap class that are super super slow,
    > gotta upgrade how vale does arrays under the hood

-   compare against rust and go!

consider combining if-lets and ors:

> if ((x) = a and exp1 or exp2) {

translate ValeJS to vale

could make array slices and string slices built into the language, to
save a clock cycle

Midas:

-   StructToInterfaceUpcast

    -   inline imms

    -   inline muts

-   InterfaceCall - calls a method on an interface

    -   inline imms

    -   inline muts

-   NewStruct - makes a new struct instance

    -   inline muts

-   MemberLoad - loads a member of a struct

    -   inline muts

-   Destroy - destroys a struct instance

    -   inline imms

    -   inline muts

-   UnknownSizeArrayStore - modifies element of an array

    -   inline muts

-   MemberStore - modifies a struct\'s member

    -   inline muts

-   Reinterpret - no op

-   UnreachableMoot - no op

-   CheckRefCount - no op

-   InterfaceToInterfaceUpcast

    -   inline imms

    -   yonder imms

    -   inline muts

    -   yonder muts

-   ConstantF64

-   NewArrayFromValues - new array from specific given values

    -   inline imms

    -   ~~yonder imms~~

    -   inline muts

    -   yonder muts

-   KnownSizeArrayLoad - loads element of an array

    -   inline imms

    -   ~~yonder imms~~

    -   inline muts

    -   yonder muts

    -   add bounds checking

-   KnownSizeArrayStore - modifies element of an array

    -   inline imms

    -   yonder imms

    -   inline muts

    -   yonder muts

-   DestroyKnownSizeArrayIntoLocals - destroys an array from
    > NewArrayFromValues, putting values into locals

    -   inline imms

    -   yonder imms

    -   inline muts

    -   yonder muts

-   DestructureKnownSizeArrayIntoFunction - destroys an array from
    > NewArrayFromValues, consuming values via interface

    -   inline imms

    -   inline muts

    -   yonder muts

-   Reinterpret - no op

-   UnreachableMoot - no op

-   CheckRefCount - no op

-   InterfaceToInterfaceUpcast

    -   inline imms

    -   yonder imms

    -   inline muts

    -   yonder muts

email:

\- thomas biskup

~~- dr clements~~

\- gel dude

Midas:

-   Shared mode

-   Borrow refs

-   Describing who is holding the constraint refs

-   Ref count elision (eliminating redundant ref count
    > increments/decrements)

-   Inline members and variables

-   Atomic immutables

-   move/copy things between threads/processes/regions

-   ptrs to outside world

-   ~~externs~~

-   cs codegen

-   weak ptrs

-   interop with c++ / native keyword

-   type info

-   deterministic replaying

-   Pure functions, bump allocating

-   folding other regions into this one

-   mutexing regions

Maybe:

-   safecalling

-   chronobase

-   structured concurrency

Valestrom:

-   we dont compile something until we use it. lets still compile it,
    > but make sure we tree shake it out anyway, somehow\... maybe by
    > doing them last? can we compile them after all the other stuff?

-   Preserve comments in parser, so we can have syntax highlighted
    > comments

-   Full test coverage

-   Borrow checking

-   Auto inlining

-   Weak pointers

-   Regions

-   Shortcalling

-   Metaprogramming

-   Harden the parser rules for for/eachI, it was hacked in and we have
    > no idea if its sustainable. perhaps:

    -   lambdas cant have a space between the params and body

    -   lambdas cant have an identifier before them (but can have a
        > space or open paren or something)

    -   function calls cant have a space between name and parens

    -   eachI must have a space between condition and body lam

    -   eachI condition cant have any braces in it.

        -   wait, maybe unnecessary. we might be able to lean on the
            > fact that exprs always have an odd number of terms. but
            > might be wise to have this restriction anyway.

-   to fix the destroy order bug, we should probably thread through a
    > \"what we\'re currently processing\" stack, and maybe switch to a
    > breadth-first model.

-   take out the redundant env lookups, just transform the A name to a T
    > name first at the call site

-   take the unevaluatedContainers out of the FunctionEnvEntry, we want
    > to put them into the Templata as we take it out of the env.
    > remember Map:Entry(4, 5), thats when we want to keep track of
    > unevaluated containers. maybe keep track as we descend like that?

-   Do we need DiscardH? or is everything in a block\'s init a discard
    > automatically?

-   is prototypeToAnonymousSubstruct redundant with
    > makeAnonymousSubstruct? maybe ones an optimization?

-   AnonymousSubstructImplName2 appears to be useless?

-   is scout\'s envFullName useless?

-   add test for when we return two different subclasses from an if

-   add a templar test for a templated closure

-   StructTemplata and InterfaceTemplata seem redundant

-   take the env out of StructTemplata and InterfaceTemplata, so that
    > envs dont escape into the hammer?

-   we assemble edges in both EdgeTemplar and carpenter, are they doing
    > the exact same thing?

-   what happens if we return in the middle of an argument to a return?
    > do we throw away the previously calculated arguments?

-   take out Void2 and VoidH, instead have the empty pack struct ref.

-   make Array take in a functor, not an interface instance.

-   \_\_addIntInt is a function2 in our output, maybe we can make it
    > easier on llvm? maybe we can do our own inlining, or avoid that
    > step? probably just takes some special stuff in the
    > overloadtemplar\...

-   make unity reloading dlls a little better:
    > [[https://www.forrestthewoods.com/blog/how-to-reload-native-plugins-in-unity/]{.underline}](https://www.forrestthewoods.com/blog/how-to-reload-native-plugins-in-unity/)

Add to Manifesto:

-   Corrects a bunch of c++ mistakes:

    -   Offering delegation only with inheritance

    -   \'this\' being special:

        -   Java needs OuterClass.this because things are ambiguous

        -   JS needs \"const self = this\" and .bind and nonsense
            > because of it

        -   any parameter can be virtual

        -   can destructure this if you want to

        -   can do UFCS, moo.bork(5) can become bork(moo, 5), because
            > theyre all just regular parameters

        -   inheritance needs a notion of \"this\". without this being
            > special, we can compose multiple things together.

        -   \'this\' was done so we wouldnt have to type \'this.\'
            > before every member variable, but we can use \'using\' on
            > any parameter.

    -   mutate ordering (destroy then move, vs correct move then
        > destroy)

    -   destructor ordering (destroy members then container, vs correct
        > container then members)

Add to Single Ownership section once we collect the other benefits:

Single ownership has other benefits too:

-   Better error handling, because destructors can now take parameters
    > and return things.

Change expect to !!, make ! return a DerefNoneError

Another central theme: reduce risk. We should have graceful transition
off Vale; it should be easy to go from Vale back to other
languages/platforms.

Research:

-   Can any other languages be a common business logic layer? Can they
    > cross-compile? Ask rust and swift subreddits.

-   Doublecheck our borrow references

Genesis:

-   Pattern matching from Scala (and all the other functional langs)

-   Borrow checking from Cyclone

-   Fat pointers from Rust

-   Constraint Refs were from a misunderstanding of Rust, but also
    > appeared in GelLLVM

-   Regions were halfway between superstructures and some sort of
    > amorphous mutexed area of memory. Closest thing is Cone\'s.

-   Owning references and destructors from C++

# Done

~~optional:~~

-   ~~fix windows build~~

-   ~~make an article: generational memory part 2: inline locals~~

    -   ~~or, just add them to the generational references post~~

    -   ~~then adjust HGM to be more about scope tethering and static
        > analysis, and stop saying its so complicated~~

-   ~~update raii and zero articles to mention that we no longer use RC
    > for resilient mode~~

```{=html}
<!-- -->
```
-   ~~(at some point) polish surprising weak ref implementations~~

-   ~~(at some point) polish home page, link out to various articles,
    > mention how its a prototype, mention vale\'s goals~~

-   ~~write the fearless FFI article~~

~~To unblock Theo\'s program:~~

-   ~~Simplify all the function-receiving instructions~~

-   ~~Add a couple global counters for how many liveness checks actually
    > happen during runtime~~

-   ~~update hgm article with scope tethering~~

-   ~~put hgm article up but dont post it anywhere.~~

~~valestrom:~~

-   ~~make it so we can iterate over KSAs.~~

    -   ~~for this reason, 1-element tuples should indeed be KSAs.~~

```{=html}
<!-- -->
```
-   ~~finish lock example in references page~~

-   ~~finish in generics page the: struct Flock\<T impl ISpaceship\> {~~

-   ~~take out readable focus, maybe call it simple instead~~

-   ~~take out mention of vale build, vale run, etc commands, link out
    > to the building+running section on the downloads page or
    > something~~

-   ~~rename from .vir to .vast~~

-   ~~link to contributors page from home~~

-   ~~add a download page~~

```{=html}
<!-- -->
```
-   ~~finish ffi for simple structs? can even be temporary. make midas
    > generate the c header.~~

```{=html}
<!-- -->
```
-   ~~rewrite entire guide~~

    -   ~~Examples show the use of Int and Str, but as far as I have
        > tested, it\'s int and str (lowercase)~~

-   ~~variant indexing sample on site doesnt have the \"planned
    > feature\" side note~~

    -   ~~sweep entire site, find all of these missing clocks~~

-   ~~change the electrons to \[17\] etc~~

    -   ~~or click on it to highlight it~~

-   ~~change front page sample to add parens (or upgrade the parser to
    > handle that)~~

    -   ~~could just do a expr level 9~~

-   ~~update interfaces page, thats not the actual syntax~~

    -   ~~flip the impl order in the site, its backwards lol~~

-   ~~change each (blah) { \... } to each blah { \... }~~

-   ~~take vec.vale out~~

-   ~~swap order, so its impl Bipedal for Human~~

```{=html}
<!-- -->
```
-   ~~Update posts to have &! references~~

-   ~~Change avatar: Remove V and put black out around it~~

```{=html}
<!-- -->
```
-   ~~report correct positions for multiple files!~~

-   ~~Templar should say when the types dont match~~

-   ~~add extern~~

```{=html}
<!-- -->
```
-   ~~maybe instead of checking the types of refs, we can check the mode
    > more often?~~

-   ~~move strings into the regions too~~

-   ~~turn all getEffectiveWeakability into region checks~~

```{=html}
<!-- -->
```
-   ~~make some proper unambiguous names for these things, and can
    > shorten ones that arent extern~~

~~make it so an if-statement will find a common ancestor, so we can
return none or some from if-statement~~

~~make valetest.py invoke valec.py, with a subprocess~~

-   ~~correctly detect a dangling pointer dereference~~

```{=html}
<!-- -->
```
-   ~~email clements~~

~~make it so we can input .vir~~

~~benchmark v1 against v3, so we can see how much more expensive stack
is.~~

~~fixed the parser positions~~

~~Make it so we can run the vale compiler from outside the vale
directory~~

~~fix error reporting position for expressions, make it at least say
that the problem is somewhere inside a certain statement~~

~~fix importing situation:~~

-   ~~figure out how to include files from stdlib:~~

    -   ~~A. include all these stdlib .vale files in every compilation~~

    -   ~~B. add an import statement~~

~~(1 hour)~~

-   ~~better error message for:~~

> ~~arr = Array\<mut, int\>(10, &IFunction1\<mut, int, int\>((index){~~
>
> ~~innerArr = Array\<imm, int\>(1, &IFunction1\<imm, int,
> int\>((index2){~~
>
> ~~index2~~
>
> ~~}));~~
>
> ~~ret innerArr;~~
>
> ~~}));~~
>
> ~~\"C:\\Python\\Python38-32\\python.exe\" ./../Vale/valec.py\^~~
>
> ~~./../Vale/vstl/arrayutils.vale\^~~
>
> ~~./../Vale/vstl/printutils.vale\^~~
>
> ~~./../Vale/vstl/castutils.vale\^~~
>
> ~~./../Vale/vstl/opt.vale\^~~
>
> ~~./../Vale/vstl/list.vale\^~~
>
> ~~./../Vale/vstl/hashmap.vale\^~~
>
> ~~%\*~~
>
> ~~(1 hour)~~

~~add infer-ret~~

~~make integrationtestsA do the upcastif test~~

-   ~~add fn print(s StrSlice) {} to
    > [[https://pastebin.com/uMf4KwvT]{.underline}](https://pastebin.com/uMf4KwvT)
    > to see crash~~

    -   (couldnt repro, cuz link expired)

-   ~~\' crashes inside a string~~

-   ~~need \" inside string~~

-   ~~extra args, like = StrSlice(s, begin, end, \"\");, crashes~~

-   ~~mutating a imm struct crashes Midas~~

-   ~~readValidChars(c, \" \\r\\n \"); // Why do I need a space in the
    > end?~~

-   ~~detect, impl &MutabilityP, should be impl MutabilityP~~

~~figure out where strings.c and strings.vale are, and put them into the
standard library~~

~~figure out how to do fast-assert.~~

-   ~~perhaps replace the function in the resulting assembly, and inline
    > it ourselves?~~

-   ~~or~~

    -   ~~fix midas to use i8\*~~

    -   ~~use clang to make all the C into llvm~~

    -   ~~combine it all~~

    -   ~~run opt~~

    -   ~~measure~~

    -   ~~manually replace with multiplication~~

    -   ~~measure~~

    -   ~~move all optimization passes into midas~~

    -   ~~add a very last pass that looks for the
        > write-invalid-or-valid~~

    -   ~~replace that with a multiplication~~

-   ~~the above was obsolete. turns out, an if-statement plus null
    > dereference is faster.~~

~~detect when we have multiple files that have the same function (like
main)~~

~~(1 hour)~~

~~Fix compiling with two mains~~

~~Fix compiling with two functions with same signature~~

~~- finish struct, interface, and rest of the expressions~~

~~(4 hours)~~

~~mutating after moving:~~

-   ~~identifyingRunes = IdentifyingRunesP(Range(0,0),
    > List\<IdentifyingRuneP\>());~~

-   ~~mut structP.identifyingRunes = Some(identifyingRunes);~~

-   ~~mut identifyingRunes.range.end = 1;~~

~~Better error message for subscripting non-subscriptable types:~~

> ~~fn main() {~~
>
> ~~l = List\<int\>();~~
>
> ~~l.add(1);~~
>
> ~~l.add(3);~~
>
> ~~l.add(7);~~
>
> ~~println(l\[0\]);~~
>
> ~~}~~

~~(1 hour)~~

~~Fix the impl limit!~~

~~Optimize:~~

-   ~~Stop using persistent data structures~~

-   ~~Change lookup to not linear search the entire environment~~

-   ~~Make it so we can lookup impls by the struct templata~~

~~add externs support!~~

~~Make parser output JSON~~

~~Better error message for using/mutating an undeclared local~~

~~its awkward that both samples and stdlib are together in the Samples
dir~~

-   ~~split them!~~

~~optingarraylist.vale -\> list.vale~~

~~make a script for making the distributions~~

-   ~~make sure it includes stdlib~~

-   ~~change script to use clang-7 instead of clang.~~

~~(1 day)~~

-   ~~make ValeLang repo~~

Midas:

-   ~~Ref counting for immutable structs/interfaces~~

-   ~~Destructors for immutables~~

-   ~~Borrow checking for mutable structs/interfaces~~

-   ~~Argument~~

-   ~~Block~~

-   ~~Call~~

-   ~~LocalLoad~~

-   ~~Return~~

-   ~~ConstantI64~~

-   ~~Stackify~~

-   ~~ExternCall~~

-   ~~Unstackify~~

-   ~~If~~

-   ~~While~~

-   ~~ConstantBool~~

-   ~~StructToInterfaceUpcast~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~InterfaceCall - calls a method on an interface~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~NewStruct - makes a new struct instance~~

    -   ~~inline imms~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~MemberLoad - loads a member of a struct~~

    -   ~~inline imms~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~Destroy - destroys a struct instance~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~LocalStore - modifies a local variable~~

-   ~~MemberStore - modifies a struct\'s member~~

    -   ~~yonder muts~~

-   ~~DestroyUnknownSizeArray - destroys an array from
    > ConstructUnknownSizeArray, consuming values via interface~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~ConstructUnknownSizeArray~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~UnknownSizeArrayLoad - loads an element from an array~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~ArrayLength~~

    -   ~~yonder imms~~

    -   ~~yonder muts~~

-   ~~ConstantStr~~

-   ~~fix the multi-return problem~~

    -   ~~put unstackify check back in~~

    -   ~~fix the move-from-inside-if thing. i bet its blockhammer
        > discarding the unstackifieds from inner blocks.~~

    -   ~~make it so midas copies the locals map like blockhammer
        > does.~~

-   ~~UnknownSizeArrayStore - modifies element of an array~~

    -   ~~yonder muts~~

-   ~~Roguelike~~

    -   ~~goblins hash map~~

```{=html}
<!-- -->
```
-   ~~Dont do a single page app~~

Valestrom, pre-release:

-   ~~Fix coord borrow coord rule~~

-   ~~each loops~~

-   ~~Templated array length function~~

-   ~~Syntax highlighter infrastructure~~

-   ~~\'this\' syntax for constructing. Perhaps just transform it in
    > scout, even. yeah, theyre basically just variables with dot in the
    > name, not member accesses, and then we implicitly construct
    > \`this\` before the first usage or at the end of the function.~~

    -   ~~might want to call the actual constructor in templar\... OR
        > use named parameters.~~

    -   ~~before constructing, \`this\` is some sort of tiny
        > namespace.~~

    -   ~~maybe this = lazy MyStruct; ?~~

    -   ~~maybe we should say \`construct this\` in the param?~~

    -   ~~if we say \`construct this\` in the param, it really drives
        > home that we construct into something we\'re given, which is a
        > little more true to the metal.~~

-   ~~destruct, just replace with destructuring, easy.~~

-   ~~Lowercase int bool void str etc~~

-   ~~Syntax highlighter complete~~

Site:

-   ~~Reference: Basic expression syntax~~

-   ~~Reference: Functions~~

-   ~~Reference: Structs~~

-   ~~Reference: Interfaces~~

-   ~~Reference: Templates~~

-   ~~Reference: Pattern matching~~

-   ~~Reference: Memory model~~

-   ~~Blog: Next Steps for RAII (published)~~

-   ~~Blog: The Magic of Inline: (drafted)~~

-   ~~Blog: Single Ownership: The Key to LLVM/JVM/CLR/JS Cross
    > Compilation (drafted)~~

-   ~~Blog: Single Ownership + Regions = Ref-Counting Speed (draft)~~

-   Reference:

    -   ~~show not-yet notes~~

-   ~~make date more prominent~~

-   ~~get rid of all instances of cone in the code~~

-   ~~clean up r/vale~~

~~Reference Intro page:~~

-   ~~Fix downloads link~~

-   ~~Fix variant indexing link~~

-   ~~s/tutorial/introduction~~

-   ~~Perhaps make the code and output not side-by-side, but on top of
    > each other like in the home page, and have text to the left?~~

~~References page:~~

-   ~~Take out the jab at rust~~

~~Put in an \"AKA GelLLVM\"~~

~~First post will be to r/programminglanguages, and have midas and a
basic reference, no blog posts or roguelike example. Will ask them what
they think, what they like about the language, what they dont like, what
the potential might be.~~

~~RAII post:~~

-   ~~DEFINITELY support all the syntax we\'re showing. or at the very
    > least, have some notes at the end talkimg about whats different
    > from whats implemented. maybe note to it from the first mention of
    > vale. now that v\'s fucked up everything, we need to be super
    > careful about over-promising. maybe say \"we want to be very clear
    > about what is and isnt implemented in vale yet, and not
    > over-promise.\"~~

-   ~~add other uses of destructors~~

-   ~~add discord link~~

-   ~~Update examples to have &! references~~

-   ~~say \"we\'re rapidly approach our v0.1 release\"~~

-   ~~move rust stuff to an afterword.~~

    -   ~~talk about how rust gets it wrong by having a zero arg drop
        > trait, and assuming that something without it can just be let
        > go. missed such a great opportunity!~~

-   ~~change the ending:~~

    -   ~~say that vale can let the objects ask you, \"how do you want
        > to end/stop/close/finish this?\"~~

    -   ~~talk about how we can require doing one of N things, with
        > params, and returns\... not the list of features.~~

-   ~~find some more examples of wanting multiple destructors.~~

    -   ~~errors! must markHandled, or printStackTrace, or exit.~~

    -   ~~maybe futures? if a future is going to react to something,
        > maybe you shouldnt implicitly drop it. maybe you should
        > resolve or cancel it.~~

-   ~~find some more examples of returning from destructors.~~

    -   ~~might choose to return an optional owning ref to self, in case
        > it could fail.~~

    -   ~~returning an error?!~~

    -   ~~return the result of the thread\'s calculations!~~

-   ~~Nitpik: mixed metaphors. I\'ve seen comparisons to wild-west law
    > enforcement, forest spirits, and raccons in trenchcoats. Maybe
    > just stick to one thematic background~~

    -   ~~lets take out the raccoons and forest spirits. do much heavier
        > on wild west theme~~

-   ~~solidify constraint refs:~~

    -   ~~Under the Constraint Reference section, I think a brief
        > inlined definition of what \"Constraint Reference\" would be
        > helping from the onset. No one wants to pop-open an 11-page
        > PDF to dig around for a good definition of the concept.~~

    -   ~~Somewhere, talk about the real potential of constraint refs
        > making for much safer games? catching problems sooner?~~

    -   ~~mention sandboxed like wasm~~

    -   ~~mention move semantics somewhere?~~

-   ~~change lobster link~~

-   ~~maybe rename to single ownership and raii~~

~~Make an introduction to Vale post on the site, for those who read RAII
and want to learn more about Vale. also link to it from the RAII post.~~

Compiler:

-   ~~Make a script for compiling. This is silly.~~

-   ~~Update the README so people can actually run vale programs.~~

~~Make a how-to-contribute channel, locked so only i can post, and says
what interesting projects we have lined up~~

-   ~~get this to work: java -cp
    > Valestrom/out/artifacts/Driver_jar/Driver.jar
    > net.verdagon.vale.driver.Driver highlight stdin: -oh stdout: \|
    > sed -e
    > \'s!«\\(\[0-9A-Za-z\]\*\\)»!\\{this.noteAnchor\\(\"\\1\"\\)\\}!\'~~

-   ~~make a little markdown++ compiler for the site, maybe even
    > including the code\... also we can do notes with comments like
    > /\*\[1\]\*/ or we can make a special comment char like «1». then
    > start making posts for the junior compiler devs on r/pl, and ask
    > them if they want to work on a parser at the end.~~

```{=html}
<!-- -->
```
-   ~~do weak refs~~

    -   ~~from temporaries~~

    -   ~~from locals~~

    -   ~~locking~~

14. ~~make the roguelike in rust~~

    -   ~~terrain generator~~

        -   ~~use a lot of vecs.~~

    -   ~~use a lot of heap allocations.~~

        -   ~~allocate lambda callbacks for the responses~~

        -   ~~have trait object components for the units\' components~~

    -   ~~use a lot of vecs. if we gonna beat rust, its gonna be from
        > bump calling to avoid resizing costs.~~

        -   ~~components should have vecs! in fact, vec of icomponent.
            > then they ALL have to be heap allocations, lol. but, i
            > suppose this is the same for both, since these are allocd
            > outside the pooling. is there any time we\'d use
            > components when pooling?~~

    -   ~~units listen to events, take the world as readonly, do a bunch
        > of calculations, return an effect which is a heap allocated
        > lambda basically.~~

    -   general idea:

        -   when we about to attack, ask all components if they can
            > return not an imperative lambda but an adjustment lambda.

        -   ~~when doing the A\*, can avoid a bunch of allocations with
            > bump allocating.~~

        -   map should be irregular, maybe with voronoi relaxation, and
            > every node can have pointers to its neighbors. the voronoi
            > relaxation can use bump calling too.

    -   ~~lots of pathfinding and BFS.~~

        -   ~~lots of explosions, where we can BFS the spots within 4
            > spaces.~~

        -   lots of summons, and have the enemies BFS look for anyone
            > within 6 spaces to attack. (maybe not, might as well loop
            > over all enemy units)

        -   many scrolls of challenge which cause all enemies to path
            > towards you when you use it.

    -   ~~lots of dyn. we make a dyn for every desire. we need to make
        > it so we have lots of capabilities with interesting desires.
        > lots of items, each with their desire.~~

~~weds: parser errors, just make it report the correct line~~

~~thurs: AI for the units~~

fri:

-   ~~scout errors, pipe the line info through~~

-   ~~chase and attack~~

implementation steps:

-   ~~chase and attack~~

    -   ~~might need to use downcast-rs for the desire to change the
        > capability\... can we hand in the desire into the capability
        > mutably? maybe can then use downcast-rs inside the
        > capability.~~

-   ~~shout (not past walls) with BFS~~ already using BFS for getting
    > all units in sight range.

-   ~~add tile components, so that tiles can have things like items.
    > best use them for weapons. it also means our components are lots
    > of little dynamic objects, which will hurt rust. we\'ll have them
    > in an allocator so we\'re fine.~~

-   ~~or, add unit components?~~

-   ~~then maybe ill add a explode-on-death component~~

-   ~~a tiny AI for the player to keep hunting goblins on the level~~

```{=html}
<!-- -->
```
-   ~~add a thing at the bottom that says \"leave comments here!\"
    > (reddit post)~~

```{=html}
<!-- -->
```
-   ~~parser errors~~

-   ~~compiler errors~~

-   ~~flip impl struct/interface order~~

-   ~~flip abstract extern and pure in docs~~

    -   ~~Parser should at least tell us what top level thing the error
        > was at. Or rather, where it failed to keep going.~~

        -   ~~could even do it at a finer level. could even gradually
            > call into smaller pieces of the~~

        -   ~~parser. could point out which statement something\'s
            > busted.~~

```{=html}
<!-- -->
```
-   ~~add options infrastructure so we can hand in global options to the
    > templar~~

    -   ~~add a verbosity flag so we can still have our annoying
        > printouts but they wont appear to the user~~

```{=html}
<!-- -->
```
-   ~~clean up main.cs - 30m~~

    -   ~~make it output something pretty. \"compiling\...\"~~

-   ~~make a branch of ValeLang as it was as of hackathon~~

-   ~~add instructions for how to run Midas stuff. add some benchmark
    > methodology and raw output too - 1h~~

-   ~~incorporate kkairos feedback - 30m~~

-   ~~update numbers, say it was with 200x200 maps~~

-   ~~mention that we fired up a compile server - 5m~~

-   ~~sort the things in the scope afterword - 5m~~

-   ~~- color and uncomment the lines in the snippet~~

~~resilient mode is incrementing something inside the object\... the
only way things work is if we include the census things in the control
block.~~

-   ~~fix census mode! we havent been using it lol~~

```{=html}
<!-- -->
```
-   ~~make a struct that contains various bits of information about the
    > region, that these functions can all operate with. describes what
    > intel is available.~~

-   ~~Clean up metal AST~~

-   ~~Finish cross platform core post, make a page out of it~~

    -   ~~doublecheck that we have to do message passing to the core~~

-   ~~make a battle plan doc~~

Fixes and necessary upgrades:

-   ~~this fails: fn main() { println(3); } because LLVM expects main to
    > return an int~~

-   ~~this fails: fn main() \[\] { \[\] }~~

-   ~~add a println(bool)~~

~~Rescue Midas from its dire situation~~

-   ~~Statically type all LLVMValueRef handling~~

-   ~~Move all region code into IRegion subclasses~~

```{=html}
<!-- -->
```
-   ~~Put the region in the referend~~

-   ~~Make Midas duplicate all the imm structs and give them regions so
    > theyre all pointing to linear.~~

-   ~~Make it somehow remember what structs correspond to what structs.
    > Temporary; later we can use the virtual generic copy function to
    > do this for us.~~

-   ~~get interfaces to work~~

-   ~~repair all the broken tests~~

-   ~~add the doublechecking mode for theo~~

-   ~~get unserializing to work, so we can send stuff back from c~~

-   ~~make it include the size and maybe the start address~~

    -   ~~should work only for imms but thats fine~~

~~add a better message for midas about finding no main~~

~~path to out of the mist and into interesting open fields~~

-   ~~finish refactor~~

-   ~~make externs work like regions~~

-   ~~do externimmusa~~

```{=html}
<!-- -->
```
-   ~~make some awesome terrain generators~~

    -   ~~for small levels perhaps?~~

-   ~~put in some primitive timed explosions, explosion traps, and maybe
    > some explody elementals~~

-   ~~make good use of terrain in combat? see if it feels good.~~

-   ~~maybe have blinking? some abilities you can only use every once in
    > a while?~~

```{=html}
<!-- -->
```
-   ~~Add regions to Valestrom coords, bring the serialize methods in as
    > a virtual generic and/or generator, so we can recursively copy to
    > another region.~~

~~replace executeProgram with something that can write to the command\'s
stdin, and read from its stdout. perhaps use [[this
code]{.underline}](https://stackoverflow.com/questions/47579087/how-to-run-another-program-pass-data-through-stdin-and-get-its-stdout-output-i).
perhaps also use a struct that contains the error code, stdout, and
stderr. then, add a test or two.~~

-   ~~add better support for static arrays~~

-   make an extern that will concat an array of strings together.

-   ~~171: Switch int from i64 to i32~~

~~module support:~~

-   ~~make valestrom take in directories. if it takes in a directory,
    > itll read all the vale files from it, shallowly.~~

-   ~~add import statements, and itll pull in the files from that
    > directory~~

    -   ~~the first thing in the import statement will be the package
        > name, which we can alias from command line~~

-   ~~159: Add Preprosessor macros to auto gen headers~~

-   ~~154: Make ValeStr.h from Midas~~

-   ~~imm array from list~~

-   ~~static arrays~~

```{=html}
<!-- -->
```
-   ~~publish externs page~~

-   ~~have a pure keyword~~

~~Change ints to 32 bits, before saying how to do extern calling~~

~~switch to
[[https://github.com/sheredom/subprocess.h/blob/master/subprocess.h]{.underline}](https://github.com/sheredom/subprocess.h/blob/master/subprocess.h)
for subprocesses~~

-   ~~174: Finish subprocess library~~

-   ~~Turn on asan if census is enabled. maybe rename it?~~

```{=html}
<!-- -->
```
-   ~~figure out RC across the boundary, even a temp solution. maybe
    > just dont allow rc mutable externs.~~

-   ~~finish mutable handles~~

-   ~~get KSAs to work, cant pass them as params right now.~~

-   ~~uncomment extimmksaparam test~~

~~make it so we cant mutate final locals~~

~~change 2 suffix to T~~

~~Clean up the output files / temp files situation, we make too many
temporary files, in different places~~

-   ~~rename unknown-sized-array to runtime-sized-array~~

-   ~~rename all the templar instructions to end in TE instead of 2~~

-   ~~Finish splitting Assist off of the Mega region~~

-   ~~Split the other regions off of the Mega region~~

~~upgrade to LLVM 11 or 12, such that we have a similar setup for
windows and ubuntu~~

~~add tests for sending owning references across the extern boundary~~

-   ~~Make mobile version~~

-   ~~post generational references to HN/reddit~~

~~Make hammer local IDs unique per function~~

~~Make an efjkl++ mode that actually doublechecks that catalyst was
right.~~

-   ~~162: remove the requirement for main~~

-   ~~173: Get rid of valec.py~~

-   ~~172: Fix HGM on Windows~~

```{=html}
<!-- -->
```
-   ~~Make sure the extern calling works on windows~~
