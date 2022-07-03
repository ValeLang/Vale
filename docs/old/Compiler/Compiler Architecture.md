Parser will take in a file and produce the first AST. Should be
completely parseable without reading other files. Important for syntax
highlighting in IDEs. This is the raw AST, suffix R.

Will also figure out what closured variables there are, and whether
theyre mutated from inside closures.

Will also figure out if they're moved from inside closures.

The only way we can figure that out is if we disallow
move-method-calling.

moo.blah() can't consume moo. The only real case for this though is
destructors, and perhaps for those we should have a "dest moo;"
statement (if any at all). Otherwise, they're always free to say
blah(moo) to move.

This also means that myList..shout() is necessarily borrowing a list,
and borrowing the elements inside it. so does the equivalent
myList.map({\_.shout()}). if you want something that consumes the
elements as moved, you want myList.drain({\_.kill()}).

It's awkward, because if we have:

let x = doSomething(\...);

doOtherThings(x);

parser doesn't know if that's really moved or borrowed... because it
doesn't know whether that's an owning reference or a borrow reference,
and borrow references can't be moved. hOwEvEr, parser isn't tracking
whether it's moved, it's tracking "if it happens to be an owned, whether
it's moved."

This knowledge is used later on; when Templar figures out the type of x,
it'll combine it with parser's knowledge of whether it's been moved or
mutated by self or closures, and figure out whether it should be an
Addressible right there.

A denizen is a thing at the top level of a file, like structs,
functions, impls, exports, etc.

A citizen is a struct or interface.

if we didn't do this in parser, we'd have to have some sort of lookahead
nonsense in the templar.

use poisoning:
[[https://www.reddit.com/r/Compilers/comments/9g2d4f/any_resources_or_best_practices_for_error/e62zrfa/]{.underline}](https://www.reddit.com/r/Compilers/comments/9g2d4f/any_resources_or_best_practices_for_error/e62zrfa/)

should we contain the void type to only the boundary between us and the
outside world?

Astronomer:

-   Figures out the types of all template runes.

-   For each local variable, collects the results of scout\'s scouting
    > to know whether the variable should be addressable, and combines
    > that back into the original LocalVariable.

For Templar, see [[this
doc]{.underline}](https://docs.google.com/document/d/1vfK8DGPKjpvS3eb-sVtOL_3jjtvP1ZKCn0cQ-Yoe1QY/edit).

For Hammer, see [[this
doc]{.underline}](https://docs.google.com/document/d/1sVc4Ti7HaR61ku8FhtbIO7ha_V0j0kMTiKZs9cStwjI/edit).

we do all the below things in bottom-up order in the call graph.

NumTracker, an alternative to CrumbTracker, which puts ref counts in.

-   First, classifies parameters (and/or):

    -   Escapes to wherever; put into a struct which escapes to
        > somewhere,

    -   Escapes to a unique inside the return value,

    -   Escapes as the return value,

    -   Doesn't escape.

    -   Unknown

-   Classifies return value (and/or):

    -   Could contains stuff from parameter 1, and/or 2, 3, etc.

    -   Unique

    -   Unknown

Will need to read child calls to get this right. Will be an interesting
graph problem.

Then puts in Increment/DecrementRefCount(objectRegisterAccess, atomic).

(until it's complete though, it can just increment before passing an
immutable in, and decrement after passing them in.)

Movechecker will annotate any StructLookup and LocalLookup and
ElementLookup with the 'check' boolean, which says whether we, at
runtime, must make sure the contents weren't moved away. Before, we
always checked. Note, we can tell if a closure will move a certain
variable (namely, if we're putting it into a reference field or address
field in the closure struct, originally determined by parser).

-   If an owning from a Local/Struct/ElementLookup is move-SoftLoad'd,
    > then we have to null that local/member/element.

-   Any Local/Struct/ElementLookup after that move-SoftLoad must be
    > 'checked'.

Can be before or after Tracker, doesn't matter.

This is an optimization, since otherwise it would check every time.

Dechronifier will make a ChronoStruct/ElementLookup into a function
call, and all ChronoRefs into regular fat pointers.

Now, we should have something runnable by a very simple non-chrono-aware
VM.

\-\-\-\-\-\-\-- i feel like there's a slight division here. the below
assumes native. JVM is different.

Deviewer will turn all interface references into structs, which can have
an "itable" member.

Now we should have something convertible to LLVM.

I think the above two are interchangeable.

when hammer boxes things, perhaps provide a hint to midas that its a
tiny type and that it can inline it when it feeds to llvm. like, struct
{ %Marine\* } instead of %MarineBox.

in fact, maybe do this for more things\... such as inline functions?
gotta get around the performance hit rustc had. perhaps make
function-likes which calltemplar can watch for? theres already
extern/interface call, why not intrinsiccall too?

\-\-\-\-\-\-\-- JVM from here on down.

Addresses instead become boxes. A closure struct, instead of containing
an address member, will contain a box which points to a thing.

A structlookup of a final member would be just the reference.

A structlookup of a mutable member would be

If an expression results in an address member, it really just results in
a box (or in the case of an array, a fat pointer with the array
reference and an index)
