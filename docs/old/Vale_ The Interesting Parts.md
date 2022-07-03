Draft 5 - Last updated July 11, 2020

This doc contains our vision for Vale, and explains its direction and
potential.

# Summary

Vale is a high-level programming language that accomplishes safety and
speed with a novel memory management technique using single ownership,
constraint references and regions.

Except for immutable objects, every object has exactly one **owning
reference**, but can have any number of constraint references. When the
owning reference disappears, if there are any **constraint references**,
then (unless in release mode) the thread halts and reveals the active
references. Vale then builds upon constraint references to safely offer
**weak references**, for flexibility and ergonomics.

For concurrency and single-threaded optimization, an object can only be
referred to (via constraint or weak reference) within its own
**region**. Regions are hierarchical; every region has a parent. A
region can temporarily have references into its owned regions\' objects,
enforced at compile-time. We can slice objects from one region into a
new child region, as long as there are no constraint references within
the objects pointing outside, and none outside pointing in. We can open
a region as read-only, allowing zero-cost references into them, a common
optimization even within a single thread. Regions are also used in
multi-threading: each mutex owns a region, and each thread has a root
region.

Mutable objects use single-ownership, which need no garbage collector,
and immutable objects inherently lack cycles, so reference counting is
used when compiling to native targets. Because of regions, ref-counting
is extremely fast because it\'s non-atomic (though we can opt-in to
atomicity and cross-thread sharing per-object), and static analysis
optimizes away over 90% of all reference counting overhead. Because of
all this, Vale should have speed comparable to C++.

Vale\'s design also includes modern features such as virtual templates,
shortcalling syntax, parameterized and returning destructors,
deterministic replayability, object inlining, immutable data classes,
templates and specialization, pure functions, options instead of nulls,
concepts, and more, see [[vale.dev/ref]{.underline}](http://vale.dev/)
for more information.

[[Summary]{.underline}](#summary)

[[Themes]{.underline}](#themes)

> [[Constraint References]{.underline}](#constraint-references)
>
> [[Single Ownership]{.underline}](#single-ownership)
>
> [[Destructors]{.underline}](#destructors)
>
> [[Regions, Static Region Checking, and
> Parallelism]{.underline}](#regions-static-region-checking-and-parallelism)
>
> [[Deterministic
> Replayability]{.underline}](#deterministic-replayability)
>
> [[Better Error Handling]{.underline}](#better-error-handling)

[[Guiding Principles]{.underline}](#guiding-principles)

> [[Fail Fast, Fail Loudly]{.underline}](#fail-fast-fail-loudly)
>
> [[Safety, Speed, and
> Flexibility]{.underline}](#safety-speed-and-flexibility)
>
> [[Easy to Learn]{.underline}](#easy-to-learn)
>
> [[Clarity]{.underline}](#clarity)

[[Additional New Features]{.underline}](#additional-new-features)

> [[Pattern Parameters]{.underline}](#pattern-parameters)
>
> [[Inlined Virtual Methods]{.underline}](#inlined-virtual-methods)
>
> [[Virtual Template Methods]{.underline}](#virtual-template-methods)
>
> [[Cross Compilation]{.underline}](#cross-compilation)
>
> [[Native Calling]{.underline}](#native-calling)

[[Vale\'s Unique Features]{.underline}](#vales-unique-features)

# Themes

## Constraint References

Constraint references are still a new paradigm to many, so this section
describes them further.

Imagine you have a basic Chat app, which listens for messages from a
socket. When it receives a message, it prints it to the console and
shows a notification (Vale has no SystemUI or ShowNotification, but
pretend it exists).

> struct ChatWindow {
>
> fn ChatWindow(socket &ISocket) {
>
> socket.addListener(this);
>
> }
>
> fn receiveData(&this, message Str) {
>
> SystemUI.ShowNotification(message);
>
> }
>
> }
>
> // A class which receives data from the network and calls
>
> // receiveData on every listener.
>
> interface Socket {
>
> fn addListener(&this, listener &ISocketListener) Void;
>
> fn removeListener(&this, listener &ISocketListener) Void;
>
> }
>
> interface ISocketListener {
>
> fn receiveData(&this, message Str) Void;
>
> }
>
> fn main() {
>
> socket = \...;
>
> chatWindowA = ChatWindow(&socket);
>
> \... // user chats for a while
>
> drop(chatWindowA); // destroy the chat window variable
>
> \... // user moves on with their life
>
> }

There is a bug in this program! Specifically, removeListener was never
called! This should have been in a destructor for ChatWindow:

> fn destructor(this, socket &ISocket) {
>
> socket.removeListener(this);
>
> }

Because of this, in other languages, after the ChatWindow is destroyed,
during user moves on with their life, the Socket could be calling
receiveData on a dangling reference!

Here\'s how the bug would manifest in various languages:

-   C++: If you\'re lucky, the program will crash when Socket tries to
    > call receiveData on the dangling pointer. If you are unlucky, the
    > program will continue executing in an unsafe undefined state. On
    > top of that, someone will probably exploit this as a security
    > hole.

-   Java (and other GC\'d languages): Let\'s pretend that
    > drop(chatWindow); as Java would be chatWindow.dispose();
    > chatWindow = null;. The programmer expected the ChatWindow to go
    > away since (what they believed to be) the last reference was
    > nulled out. However, there\'s still a reference active to
    > ChatWindow, inside the Socket; The ChatWindow is kept alive. This
    > kind of reference is called a **\"rogue reference\"**, and the
    > object that it keeps alive past its dispose call is a **\"zombie
    > object\"**, also known as a \"java memory leak\". Various bad
    > things could happen if Socket calls receiveData on its rogue
    > reference, depending on what the programmer put in ChatWindow\'s
    > dispose.

    -   If dispose nulled out all of ChatWindow\'s members, then the
        > program will probably throw a NullPointerException.

    -   If not, this will execute receiveData on an object that used to
        > be a normal, valid object. Things might happen that you
        > didn\'t expect. In fact, in this case, *the zombie chat room
        > will show the user a notification, even after they close the
        > window!* Imagine if ChatRoom did something else in
        > receiveData, such as send automatic replies! A terrible bug
        > indeed.

-   In Swift, it depends on the type of Socket\'s reference to the
    > ChatWindow:

    -   If a strong reference, then it will be a rogue reference keeping
        > the ChatWindow alive as a zombie object, then call receiveData
        > on it, causing chaos similar to the Java case.

    -   If an unowned reference, then the program would halt when Socket
        > tries to call receiveData on the ChatWindow.

    -   If a weak reference, and Socket called receiveData like
        > listener?.receiveData(data) then call just wouldn\'t happen.
        > If they used something like listener!.receiveData(data), then
        > the program would halt. This is actually a good outcome
        > compared to what we\'ve seen so far.

-   In Rust, the program would look slightly different, and the behavior
    > would differ:

    -   The socket might have a generational index into a central pool
        > of ChatWindow, and \"dereferencing\" that index and calling
        > unwrap on it would crash, which is safe.

    -   If Socket had a Rc\<RefCell\<ChatWindow\>\> then it would be a
        > rogue reference keeping the ChatWindow alive as a zombie
        > object, and run receiveData on it, causing chaos similar to
        > the Java case.

    -   If Socket had a Weak\<RefCell\<ChatWindow\>\> then it would
        > behave as well as the Swift weak reference case.

The undesirable outcomes are the ones involving shared ownership:

-   Java\'s references

-   Swift\'s strong references

-   Rust\'s Rc

Given this, and that shared ownership is rarely ever the best tool for
the job, Vale does not offer built-in shared ownership references.

The most desirable outcomes we\'ve seen so far are all built upon weak
references:

-   Swift\'s weak references, and Rust\'s Weak.

-   Rust\'s generational indices are conceptually a weak reference.

-   Swift\'s unowned pointer is effectively just a weak reference that
    > expects to be pointing at something when it\'s ever used.

For this reason, Vale offers built-in weak references.

However, we can do even better. One of Vale\'s core principles is \"Fail
Fast, Fail Loudly\"; if there\'s a problem with our code, we want to
know about it as soon as possible, rather than hope the user never
triggers it. Using **constraint references**, Vale can immediately
detect the dangling reference long before it\'s used; it detects it the
instant it becomes dangling; the instant ChatWindow is destroyed.

With a constraint reference, this bug would be caught the first time the
programmer runs the application.

This is not a new concept:

-   SQL has had the notion of **foreign key constraints** for decades,
    > to ensure data consistency and catch errors as soon as possible.
    > By bringing that into the programming language, we can save an
    > immense amount of debugging time.

-   Game engines often do this (sometimes by wrapping shared_ptr).

-   [[Ownership You Can Count On: A Hybrid Approach to Safe Explicit
    > Memory
    > Management]{.underline}](https://researcher.watson.ibm.com/researcher/files/us-bacon/Dingle07Ownership.pdf)
    > explored adding this into an existing programming language

### When to use Weak vs Constraint References

Whether one uses constraint references or weak references depends on the
situation. There are two situations involving potentially dangling
references, each with its own appropriate solution.

Situation A: The object or function (the \"container\") containing the
reference is fine with the target object (the \"pointee\") being
destroyed. For example, a cache might contain weak references to
recently accessed objects, and if the pointee is destroyed, the cache is
fine with the reference becoming null.

Situation B: The container doesn\'t make sense to exist if the pointee
does not. For example, a PendingOrder might have a reference to a
Customer. In this case, we want to make sure we delete the PendingOrder
before we delete the Customer.

In that case, if we forgot to delete the PendingOrder before deleting
the Customer, it would be a bug. If we use weak references, we can
*eventually* discover this bug, *if* we ever try to dereference the
PendingOrder\'s customer. Then, we would embark on a long, frustrating
hunt for where we forgot to delete the PendingOrder before deleting the
Customer.

This is one example of where constraint references can save hours of
debugging time. A constraint reference could halt the program
immediately and helpfully say:

> Fatal error: Customers::removeCustomer (Customers.vale:50) deleting
> Customer with 1 incoming constraint reference.
>
> \- PendingOrder from OrderManager::makeOrder (OrderManager.vale:81).
>
> Field customer, set in OrderManager::associate
> (OrderManager.vale:152).

\...which means that there\'s still a PendingOrder whose customer field
is still pointing to the Customer object that Customers::removeCustomer
is trying to destroy.

## Single Ownership

Vale\'s immutable objects can be shared freely, but its mutable objects
must always have exactly one owning reference, which controls its
lifetime. This means that we don\'t have the concept of shared
ownership, such as C++\'s std::shared_ptr, or garbage collected
language\'s tendency to keep an object alive if any references to them
are active.

Those who are used to using shared ownership, and who are unfamiliar
with the single-ownership philosophy, might balk at such an idea.
However, through much experimentation with this style, we\'ve found:

-   Most of the time we use shared ownership, we could easily identify
    > one reference that still conceptually owned the data, and making
    > that into the single owner was trivial.

-   Using shared ownership has harmed understandability and readability;
    > it made it hard to determine how long a certain object *should* be
    > alive and accessible, and so hard to determine when to, for
    > example, unregister it from event sources.

-   Using shared ownership with destructors (as is possible with
    > std::shared_ptr) has actively caused many bugs, because the
    > lifetime is so unpredictable as to almost be nondeterministic.

-   It\'s rare to find a case where shared ownership is better than
    > single ownership. Almost all the cases where someone has reached
    > for a std::shared_ptr, we\'ve found a way to accomplish the need
    > with single ownership, just as easily. For example see the Clasp
    > pattern \[link here\].

Indeed, one of the goals of Vale is to show the world that it vastly
overuses shared ownership.

That said, there is the rare circumstance where shared ownership\'s
benefits outweigh the drawbacks, and Vale will enable them in different
ways which mitigate the downsides of the usual shared ownership
approaches. Some examples:

-   Sharing a file handle to read from. In this case, a \"FileManager\"
    > object should hold the owning reference, and everyone else should
    > hold a \"handle\" object to a file, where a file can have multiple
    > handle objects active. The FileManager would outlive the file
    > handles. The added benefit: when one wants to make sure there are
    > no active file handles, the FileManager can remove all of the
    > owning references to files.

-   A graph, if one *actually* wants nodes to disappear when they
    > aren\'t connected to anything. In this case, a central \"Graph\"
    > object can have all the owning references, and the nodes can refer
    > to each other via handle object. The handle object can do all the
    > bookkeeping, and the Graph object can do mark-and-sweep (or the
    > more interesting GC strategies) on an on-demand, deterministic,
    > localized basis. See the Graph object in the STL.

## Destructors

One of Vale\'s goals is to unlock the true potential of destructors.
Keep reading to see how destructors can be used for more than just
freeing memory.

In the ChatWindow example, the programmer made the mistake of not
unregistering the ChatWindow as a listener of the Socket. This is a very
common bug, where one does not \"undo\" what they originally did.

There is hope! Using single ownership, we can guarantee that
removeListener eventually gets called. Here\'s how.

First, we remove removeListener from Socket\'s public methods. Instead,
addListener can return a \"subscription\" object, which, when it\'s
destroyed, automatically removes the listener. In Vale, this is called
the **handle pattern**.

> struct ChatWindow {
>
> fn ChatWindow(socket: &ISocket) {
>
> socket.addListener(this);
>
> }
>
> fn drop(this) {
>
> socket.removeListener(this);
>
> }
>
> fn receiveData(this impl, message: Str) {
>
> SystemUI.ShowNotification(message);
>
> }
>
> }

becomes:

> struct ChatWindow {
>
> listenerHandle: ?ISocketListenerHandle;
>
> fn ChatWindow(socket: &ISocket) {
>
> this.listenerHandle = (socket.addListener(this));
>
> }
>
> fn receiveData(&this impl, message: Str) {
>
> println(message);
>
> SystemUI.ShowNotification(message);
>
> }
>
> }

A hidden benefit is that ISocketListenerHandle presumably contains a
&Socket, which will prevent us from accidentally destroying the socket
before the ChatWindow.

One last improvement: to prevent the user from accidentally ignoring the
addListener\'s returned subscription immediately (thus immediately
undoing the addListener call), we\'ll add the \@MustUse annotation to
Socket\'s addListener method, which will give a compiler error if the
return value not used.

Destructors are reputed to only be for cleaning up memory, yet here,
they\'re actually preventing bugs! This is the true potential of single
ownership and destructors: **tracking responsibility.**

### Destructors Are Just Functions

(work in progress)

### Parameters and Return Values

(work in progress)

Vale destructors can take parameters and return values, so if we wanted
to, we could add parameters to the ISocketListenerHandle\'s destructor
which it would forward to removeListener.

## Regions, Static Region Checking, and Parallelism

A **region** is an area of memory where objects live and, except within
functions, there are no references inside pointing out, or outside
pointing in.

A region follows the same rules as a semaphore. It can be closed,
write-open to one writer, or read-open to multiple readers. Using
read-open regions is extremely efficient: constraint references and weak
references are free.

By default, every thread has its own region, and has it open for
writing. We can make additional regions at any time, and specify its
memory management strategy:

-   **Bump allocator**: Memory is only freed when the entire region is
    > destroyed.

-   **Stack bump allocator**: Memory is required to be freed in the
    > reverse order it was allocated.

-   **Normal**: Normal memory allocation.

Regions are mainly used for efficiency and safety:

-   We can **regionize specific areas:** For example, if we were in a
    > game, we could have a region representing the game state. We would
    > temporarily open it for writing, close it, then open it for
    > reading. The compiler would help prevent modifications to the game
    > state, and accessing it would be fast.

-   We can **lock the main region**: For example, if a game wanted to do
    > pathfinding, we could lock the thread\'s main region as read-only,
    > and have a temporary writable region for our pathfinding
    > algorithm. The compiler would prevent modifications to the main
    > region, and accessing it would be fast. In fact, we could specify
    > \"bump allocator\" memory management to speed it up even more.
    > Note: pure functions automatically lock the main region.

-   We can lock the main region when doing FFI. Regions are stored on
    > separate memory pages, and locking a region will actually
    > write-protect its memory pages. With this, we can be certain that
    > a called function is not modifying data we don\'t want it to
    > modify.

-   By locking the main region, one can do parallelized map and filter
    > operations.

Every function by default only operates within one region. We can make a
region like this:

> fn findPath(terrain &Terrain, start Vec2, end Vec2) {
>
> path = Region\<Bump\>();
>
> pathfindingData = \'!path PathfindingData();
>
> // pathfinding logic here
>
> }

The \'!path means that we\'re calling this function in the context of
the path region. The ! signals that we\'re opening the region for
writing.

A function can operate on multiple regions like this:

> fn doThings(
>
> terrain \'main &Terrain,
>
> pathfindingData \'!path &PathfindingData) {
>
> tiles = terrain.tiles; // tiles has region \'main
>
> steps = pathfindingData.steps; // steps has region \'path
>
> }

Note how \'main has no !, that means it\'s accessing the region in a
read-only way. While in this function, no one will be able to modify the
entire main region.

### Transferring Memory Between Regions

To transfer memory between one region and another:

> fn move(from \'!source List\<Car\>, to \'!dest List\<Car\>) {
>
> carInSource = from.pop_front(); // carInSource is still in \'source
>
> carInDestination = \'!dest sourceCar; // now in \'dest
>
> to.push_front(carInDestination);
>
> }

or, shortened:

> fn move(from \'!source List\<Car\>, to \'!dest List\<Car\>) {
>
> to.push_front(\'!dest from.pop_front());
>
> }

In other words, the \'!dest is what moved it from its old region to the
new region.

When moving an object between regions, Vale will recursively scan it:

-   If an atomic immutable object, it is moved for free.

-   If a regular immutable object, the memory is copied from A to B
    > (unless all the references are from inside, in which case it is
    > moved for free). Loop over all members and do the same.

-   If a mutable object, all of its (directly or indirectly) owned
    > objects are considered a \"movement\". It will recursively go
    > through objects in the movement, and:

    -   If anything outside the movement has a constraint reference to
        > it, halt the program.

    -   If anything outside the movement has a weak reference to it, set
        > the weak ref to null.

    -   For each member:

        -   If it\'s a weak reference to something outside the movement,
            > set to null.

        -   If it\'s a constraint reference to something outside the
            > movement, halt the program.

Regions can be passed to other regions for free. This is the preferred
way to move data between regions, as it avoids any scanning.

### When to use Regions

One technique for regions is to lock the main region as read-only, to do
some calculations and return a result. This approach should be used
liberally, because:

-   It reduces the area of your code which modifies its central state,
    > which means it\'s easier to track down state modification bugs
    > (which are usually the most difficult to debug).

-   It speeds up the program, sidestepping any reference counting
    > overhead that wasn\'t eliminated by the optimizer.

-   Side effect: it makes that function much easier to test, because it
    > produces the results of the entire function as a value which can
    > easily be compared against a correct value.

Another technique is to separate some of the program\'s main state into
its own region, and give specific parts of the program write access, and
the rest read access. This has all of the benefits above. However,
there\'s a tradeoff: since there can be no references between regions,
one would have to use IDs to refer to things across the boundaries,
which aren\'t as ergonomic. One will have to weigh the tradeoff for
their own architecture.

## Deterministic Replayability

Deterministic replayability is the ability to replay a past execution,
in exactly the same way. Vale does this on an opt-in basis, with a
compiler flag. It will:

-   Records the result of every nondeterministic call. Functions that
    > read from stdin, files, network, pipes, etc are all examples of
    > nondeterministic calls.

-   For each thread, records the messages received from other threads.

-   For each mutex, records the order in which each thread accesses it.

-   Record the floating-point rounding mode of the CPU.

and the resulting recording will be stored on the hard drive.

Then, using a different compiler flag, Vale can replay the program in
the exact same way it happened the first time.

This can be extremely useful in, for example, beta or QA builds. Users
can send the recording file to the developer, so the developer can
instantly reproduce any problems. This can also make it trivial to find
and reproduce a situation that caused a dangling constraint reference to
halt the program.

## Better Error Handling

Instead of using exceptions, Vale uses Result\<T, E\> to represent a
success or a failure. It has two subclasses: Ok which contains a T, and
Err which contains an E.

### Matching Results

To do different things depending on whether there was an error, one can
use a match statement:

> myResult Result\<Int, Str\> = \...;
>
> mat myResult {
>
> Ok(num) { println(\"Success! \" + num); }
>
> Err(msg) { println(\"Error: \" + msg); }
>
> }

### Expect

We commonly expect that an operation succeeded, so we want to get the
success value, and abort if we were somehow mistaken.

> numOrErr Result\<Int, Str\> = \...;
>
> num =
>
> mat numOrErr {
>
> Ok(num) = num;
>
> Err(msg) { abort(Str(msg)); }
>
> };
>
> \...

so Vale offers a convenient shorthand for it, the \"expect\" operator,
!:

> numOrErr Result\<Int, Str\> = \...;
>
> num = numOrErr!;
>
> \...

### Map on Results

Result\<Int, Str\> acts just like any other container, like List\<Int\>
and Option\<Int\>, and so one can use the map function on it, which will
run the given lambda only if the result is a success:

> myResult Result\<Str, AnimalError\> = Ok(\"hi i am a lizard\");
>
> newResult = myResult.map((msg){ msg.split(\" \") });
>
> // newResult is type Result\<List\<Str\>, AnimalError\>, and now
> contains a
>
> // Ok(List(\"hi\", \"i\", \"am\", \"a\", \"lizard\"))

We can shorten the line from:

> newResult = myResult.map((msg){ msg.split(\" \") });

with the map operator, \*, to make it:

> newResult = myResult \* { (msg){ msg.split(\" \") };

and use short-parameter (\_) syntax instead, to make it:

> newResult = myResult \* { \_.split(\" \") };

and if we use the map-method operator, \*., which calls a method on the
success, to shorten it to:

> newResult = myResult\*.split(\" \");

### FlatMap on Results

Just like how List\<List\<Int\>\> can be flattened to List\<Int\>, a
Result\<Result\<Int, AnimalError\>, NotFoundError\> can be flattened to
a Result\<Int, AnimalError\|NotFoundError\>, with the flatMap function.

With this, one can work with different kinds of error possibilities at
the same time:

(Note: indexOf returns a Result\<Int, NotFoundError\>)

> newResult Result\<List\<Str\>, AnimalError\> = \...;
>
> indexOfBird = newResult.flatMap((lines){ lines.indexOf(\"bird\") });
>
> // indexOfBird is type Result\<List\<Str\>,
> AnimalError\|NotFoundError\>,
>
> // and contains a Err(NotFoundError());

We can shorten the line from:

> indexOfBird = newResult.flatMap((lines){ lines.indexOf(\"bird\") });

with the flatMap operator, \|, to make it:

> indexOfBird = newResult \| (lines){ lines.indexOf(\"bird\") };

and use short-parameter (\_) syntax instead, to make it:

> indexOfBird = newResult \| { \_.indexOf(\"bird\") };

and using the flatMap-method operator, \|., which calls a method on the
success and flattens the result, to shorten it to:

> indexOfBird = newResult\|.indexOf(\"bird\");

### Let Else for Errors

One might want to return from the function early when encountering an
error. For example, in:

> fn countLinesWithHi(filename Str) Result\<int, NotFoundError\> {
>
> contents =
>
> mat openFile(\"myfile.txt\") {
>
> Ok(contents) = contents;
>
> Err(e) = ret (e);
>
> };
>
> \...
>
> }

Luckily, we can use the let else (usually used with Optionals) for
result types too!

> fn countLinesWithHi(filename Str) Result\<int, NotFoundError\> {
>
> (contents) = openFile(\"myfile.txt\")
>
> else (e){ ret e; }
>
> \...
>
> }

### Bail Operator

It\'s so common to make a Let Else immediately return, that Vale
introduced the \"bail operator\", %.

> fn countLinesWithHi(filename Str) Result\<int, NotFoundError\> {
>
> contents = openFile(\"myfile.txt\")%;
>
> \...
>
> }

### Bailing Past Destructors

The bail operator % will call the destructor of all variables in scope.
For example, if we had a Transaction object, its drop method would be
called, thus closing the socket:

> fn countLinesWithHi(filename Str) Result\<int, NotFoundError\> {
>
> trans = Transaction(\...);
>
> // If an error, then trans.destructor() will be called.
>
> contents = openFile(\"myfile.txt\")%;
>
> \...
>
> }

In Vale, destructors aren\'t special, they can have return types and
parameters. So what happens if drop returns, for example, a Result\<int,
RollbackError\>? In that case, Vale disallows the % operator and makes
us handle it explicitly:

> fn countLinesWithHi(filename Str) Result\<int, NotFoundError\> {
>
> trans = Transaction(\...);
>
> contents =
>
> mat openFile(\"myfile.txt\") {
>
> Err(notFoundError) {
>
> mat drop(trans) {
>
> Ok(()) { ret notFoundError; }
>
> Err(rollbackError) { abort(\"Couldn\'t rollback transaction!\"); }
>
> }
>
> }
>
> Ok(contents) = contents;
>
> };
>
> \...
>
> }

So, we automatically call a drop(), unless it has a return type that has
no drop(), or that drop() has a return type that has no drop(), and so
on.

In Vale, an interface isn\'t required to have a drop. Our transaction
might instead expose more descriptive commit() and rollback() functions,
which take the transaction by move. In this situation, since there is no
drop(), we have to explicitly specify what happens, like above.

In C++, this would be undefined behavior, and in Java, we\'d lose the
first error when throwing the second. Vale makes no dangerous
assumptions during error propagation, and correctly requires the user to
specify what should happen.

This is only possible because Vale\'s destructors are regular methods,
which is only possible because of Vale\'s single-ownership foundation.

# Guiding Principles

There are four main principles motivating Vale:

-   Fail Fast, Fail Loudly

-   Safety, Speed, and Flexibility

-   Easy to Use

-   More Clarity, Less Magic

## Fail Fast, Fail Loudly

The \"fail fast, fail loudly\" principle, often known as defensive
programming, means that a program should detect when it\'s in an invalid
state, as early as possible, rather than continue in a corrupt or unsafe
state. There are generally two ways to accomplish this: return an error
for recoverable failures, and halt for broken constraints or
assumptions.

Imperative languages have more recently outgrown exceptions and embraced
Result, and Vale improves upon that design with parameterized and
returning destructors and shortcalling syntax, see \[page here\] for
more information.

The opposite of fail fast is \"fail slow\", where an error will lie
dormant, and cause problems later on. A classic example is how one often
returns null to signal an error, and a long time later, when someone
dereferences the null, it crashes, starting the programmer on a long,
frustrating hunt for where the null originally came from.

The biggest example of this is Vale\'s foundation of constraint
references, see the Constraint References section.

> And you\'ve got the explicit fatal failures \[...\] These are actually
> better than the implicit failures. You, the programmer, have decided,
> \"My state is too corrupt, and I cannot continue reliably\". Hope is
> not a strategy, including for your software. If your state has become
> corrupt, it is incumbent upon you to **die**, and **donate your body
> to science where it can be debugged**. - [[Bryan
> Cantrill]{.underline}](https://youtu.be/AdMqCUhvRz8?t=1282)

## Safety, Speed, and Flexibility

These are aspects of a language that are often in conflict with each
other. For example:

-   C++ has speed and flexibility, but not safety; you can do anything
    > really fast, but not in a safe way.

-   Rust has safety and speed, but not flexibility; you\'re forced into
    > particular patterns, but it\'s safe and fast.

-   Java has safety and flexibility, but not speed; you can do anything,
    > safely, but not fast.

Vale largely avoids this conflict, and does exceptionally well at all
three:

-   Safety: Use-after-free is impossible in Vale, because incoming
    > constraint references will cause a halt, and incoming weak
    > references are nulled.

-   Speed: It compiles to native code, there\'s no garbage collector
    > overhead. Reference counting overhead is 100% optimized away for
    > read-only regions, \>90% optimized away anywhere else, and most
    > importantly, non-atomic.

-   Flexibility: Constraint references are optional, one can always use
    > weak references if they want a more familiar approach, and
    > there\'s no limitations such as having only one mutable reference
    > or multiple readonly references.

Vale inlines small immutables as members, locals, and parameters, and
will do escape analysis to aggressively inline mutable objects as locals
and parameters. This often is better than making the programmer manually
figure out whether something can be inline. But, when the programmer
wants to, they can specify whether they want inline or not by using inl
and yon.

## Easy to Learn

Vale has a focus on being easy to learn:

-   It has a familiar C-like syntax to keep a low learning curve.

-   It removes some of the common confusing aspects of other languages:

    -   constructors and destructors are like any other function.

    -   \'this\' is not special; it\'s a parameter like any other.

    -   Using UFCS, methods and functions are the same thing.

-   The more complex features are opt-in, and don\'t need to be learned
    > immediately.

    -   By default, everything runs in the main region, and one can
        > opt-in to the speed and architectural benefits of additional
        > regions.

    -   By default, & makes a constraint reference, which is safe and
        > easier to use than borrow references, and if one wants to
        > optimize, they can add in borrow references.

    -   The inl keyword is automatically added in where possible, and
        > available to use manually otherwise.

-   Debugging is made easier using nondeterministic replayability, so we
    > never have to fear heisenbugs again.

## Clarity

Vale focuses on bringing clarity to programming, not just at a
syntactical level, but at a deeper level.

-   Vale\'s emphasis on single ownership brings clarity to *when*
    > objects are destroyed, thus avoiding entire classes of bugs
    > (use-after-free, zombie objects, etc.), where objects remain after
    > their intended lifetime.

-   Vale\'s deterministic replayability brings clarity to what exactly
    > is happening in a program, even in a nondeterministic environment.

-   Vale\'s simple memory management scheme means it\'s possible (and in
    > fact, part of the ABI spec) to predict exactly what\'s going on
    > under the hood.

-   Vale brings clarity to **this**, which in other languages is a
    > magical being.

-   \"Sugar, not Magic\"; Code is by default simple. Complexity is
    > opt-in, never automatic. For example:

    -   Vale requires short-calling parentheses, instead of allowing
        > implicit conversions.

    -   Vale gives the % operator instead of automatically propagating
        > errors.

    -   Vale requires an explicit & when passing temporary owning
        > references to a function expecting a borrow reference.

-   Vale purposefully rejected the more magical features:

    -   Vale eschews inheritance, and separately offers the three
        > mechanisms behind inheritance: implements, delegation, and
        > composition.

    -   No custom precedence. One must be able to tell the exact order
        > things execute in.

# Additional New Features

## Pattern Parameters

(work in progress) TLDR: We can destructure inside parameters. (Haskell
and other functional languages have this too)

## Inlined Virtual Methods

(work in progress) TLDR: For sealed interfaces, we can inline virtual
calls into the equivalent of a match statement.

## Virtual Template Methods

A virtual template is a function in an interface that has template
parameters, for example:

> interface IntOption {
>
> fn map\<T, F\>(this, func &F \< fn(Int)T) T;
>
> }

See [[Virtual
Templates]{.underline}](https://docs.google.com/document/d/1t76_DLA1NN3Gub63bav2_BUpFjseQnZjyK5QAbkW7EM)
for more.

## Cross Compilation

Vale\'s memory model, where no garbage collection is needed and
everything is conceptually a reference, is compatible with all common
platforms:

-   Navite binaries use all of the techniques described in this doc.

-   We could compile our objects to Swift objects, as they\'re based on
    > ref-counting.

-   We could compile our objects to JVM or JS objects by just ignoring
    > the inl keyword, and using an intermediate \"control block\"
    > object to help with weak refs.

-   We could compile our objects to the CLR (C#) by using C++/CLI
    > allocate and free instructions for mutable objects, and we could
    > choose to either use ref-counting or garbage collection for
    > immutable objects.

Vale is the only language that would seamlessly compile to all memory
models. This also gives Vale programmers the freedom to run their
program on whichever platform is most efficient. For example, a program
that uses a lot of allocations of immutable data might perform better on
the JVM.

## Native Calling

Since Vale can cross compile, we could have a single executable operate
half in the JVM and half in native code, we would just regard them as
different regions.

No matter what platform you\'re on, you can easily drop down into native
code for when you need extreme speed. Vale takes care of the marshalling
for you, since both sides speak the same basic data language (value,
owning, constraint, weak). The native realm is just another region.

# Vale\'s Unique Features

There are the features that only Vale can do, because of its high-level
single-ownership foundation.

-   [[Constraint references]{.underline}](#constraint-references)

-   [[Native Calling]{.underline}](#native-calling)

-   [[Destructor overloads, parameters, return
    > values]{.underline}](#parameters-and-return-values), which also
    > gives better error handling.

-   [[Cross Compilation]{.underline}](#cross-compilation).

    -   Vale is the only language that can do this because (except in
        > native functions), Vale is very high level, and only single
        > ownership is compatible with every target.

    -   This means that Vale will one day be the best choice for writing
        > cross-platform code.

    -   Swift comes close, but can\'t do it because on JVM, it would
        > have atomic increments and decrements as well as GC, which is
        > the worst of both worlds, at the same time.

Aside from these unique features, Vale is the only language that\'s
fast, safe, and easy.

-   Fast and safe, but not easy: Rust

-   Safe and easy, but not fast: Python, Java, Javascript, Scala

-   Fast and easy, but not safe: C

Vale is unique in offering all three.
