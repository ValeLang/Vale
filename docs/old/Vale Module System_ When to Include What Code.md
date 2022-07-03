This doc will help us answer these questions:

-   When is a .vale file included in the compilation?

    -   When are test files included in the compilation?

-   When is a .c (or .java or other language) file included in the
    > compilation?

See [[Requirements section (below)]{.underline}](#requirements) for some
bounds on the problem.

# How We\'ll Decide

Until we contributors have reached a consensus, or (last resort)
Verdagon makes a decision, we\'ll repeat these steps:

-   List out any more alternatives we can think of.

    -   **Anyone is welcome to add one!**

    -   Please make them understandable. When in doubt, ask someone if
        > it\'s clear.

    -   Alternatives can build upon each other, to improve upon them.
        > See [[Alternative M]{.underline}](#alternative-m-testing) for
        > an example on how to make this clear.

-   Once all our ideas are here, we\'ll go through and list the benefits
    > and drawbacks of each.

-   We\'ll have a discord call to:

    -   Narrow down alternatives we think aren\'t as promising as the
        > others

    -   Discuss the strengths of the benefits and weaknesses of the
        > alternatives.

    -   Discuss any new requirements.

Ground rules for talking about others\' alternatives:

1.  Anyone can add a comment (Insert -\> Comment) on anyone\'s
    > alternative to ask for clarification. Any critique should instead
    > go in the alternative\'s Drawback section.

2.  Anyone can add anything to the Drawbacks section of any alternative,
    > as long as it\'s very clear. See the Drawbacks section under the
    > **A: How it Works Today** alternative for examples.

3.  These do not count as valid drawbacks:

    A.  \"This alternative is ugly/inelegant.\" (Be more specific)

    B.  \"This isn\'t how I think it should work.\"

    C.  \"This alternative is confusing.\"

        -   Instead, add a comment to ask for clarification on the
            > confusing part.

        -   \"This part would confuse users\" is valid (but remember to
            > be specific)

# A: How it Works Today

(Orange means tentatively planned. Things that are implemented or
planned in this alternative are **not final decisions.** There\'s a lot
of temporary solutions in this.)

Every file is in a **namespace**, which is a collection of denizens that
are visible to each other by default.

A **denizen** is a function, struct, interface, impl, extern, or export.

If two denizens are in the same namespace, they can see/use each other
without any import statements.

At the top of every file, we can have an explicit namespace statement,
to declare which namespace all of that file\'s denizens live in. For
example:

> namespace stdlib.networking.sockets;

would say that all the denizens in this file are in the
stdlib.networking.sockets namespace.

If the namespace statement is missing, we infer its namespace from the
file path. If a file is in the ./networking/sockets directory inside the
stdlib project\'s source directory, we automatically infer that it\'s
part of the stdlib.networking.sockets directory.

If a denizen wants to see a denizen from another namespace, it needs to
use an **import** statement:

> import stdlib.stringutils.\*;

## When do we load and parse a .vale file?

Today, when someone imports a namespace, we load and parse every file in
the corresponding directory. Someday, we\'ll load and parse every file
that says it belongs to that namespace, regardless of what directory
it\'s actually in.

## How do we know where to find a namespace\'s files?

The directories for namespaces are specified via the command line. For
example, in\...

> python3 \~/Vale/release-unix/valec.py build stdlib:\~/stdlib/src
> markvale:src markvale \--output-dir build \--add-exports-include-path

\...the relevant two arguments are:

> stdlib:\~/stdlib/src
>
> markvale:src

because they contain a :.

Both of those each say, \"when someone tries to import something, you
can find it here in the file system.\"

This means that, for example, anything in the stdlib.stringutils
namespace can be found in the \~/stdlib/src/stringutils directory.

## Where does compilation start?

Since we only compile a namespace when it\'s imported, who does the
first import?

The \"starting namespaces\" are specified via the command line. For
example, in\...

> python3 \~/Vale/release-unix/valec.py build markvale
> stdlib:\~/stdlib/src markvale:src \--output-dir build
> \--add-exports-include-path

\...the relevant argument is markvale, which specifies it as a
**starting namespace**. We will compile everything in that namespace.

So, the above example will compile the markvale namespace, and every
namespace it indirectly depends on.

## Running a Test

There\'s no special rule for tests, we already have all the tools we
need.

To run a test, we simply specify **the test\'s namespace** as the
\"starting namespace\" (explained in previous section). For example:

> \... build **stdlib.stringutils.test** stdlib:\~/stdlib/src \...

Nothing else imported stdlib.stringutils.test, so this is the only way
to include that namespace in the compilation at all.

## When do we link in a .c file?

TL;DR: when a namespace is included, we include the C files in its
native/ subdirectory.

When we have an extern function:

> struct Socket export { fd int; }
>
> fn writeToSocket(socket &Socket, contents str) void extern;

the resulting .o assumes that that function will be present. In other
words, clang assumes that we\'ll have some C code that defines it:

> #include \"stdlib/networking/Socket.h\"
>
> void stdlib_writeToSocket(stdlib_socket\* Socket, ValeStr\* contents)
> {
>
> \...
>
> }

The C code and the vale code here are tightly coupled: the above vale
assumes that the above C function exists, and the above C function
assumes that the file stdlib/networking/Socket.h was generated. So, **if
one is included, the other must be as well.**

So, because of this, valec.py makes sure that when we compile a
namespace, we also include any of the namespace\'s native/
subdirectory\'s C files in the clang invocation.

valec.py does this by looking in the externs section of the .vast, which
lists all namespaces that had externs. It will then include those C
files in the clang invocation.

## How does Fountain deal with this?

Same as valec.py does: it looks in the externs section of the .vast,
which lists all namespaces that had externs. It will then include those
C files in the clang invocation.

## How Fountain Sees namespaces

Fountain sees a fountain.toml, and a src/, for any C code it sees a
native/, it also sees . as meta data (this is because if source wasn't
separate then . would quickly become ugly, with toml and vale, and vast
and vpst and c)

Fountain:

-   Looks at fountain.toml to figure out what namespaces we depend on

-   If needed, downloads those namespaces from our servers (source of
    > truth is often github)

-   Invokes Valestrom with the correct parameters.

## Drawbacks

### Too Many Native Directories

So, because of this, valec.py makes sure that when we compile a
namespace, we also include any of the namespace\'s native/
subdirectory\'s C files in the clang invocation.

-   **verdagon:** Not a drawback, but this seems needless. As long as we
    > can split a massive codebase (like stdlib) into multiple projects,
    > I feel like it\'d be sufficient to have one native/ directory per
    > project, not per namespace.

### Looking In Vast

valec.py does this by looking in the externs section of the .vast, which
lists all namespaces that had externs. It will then include those C
files in the clang invocation.

-   **verdagon:** I\'m hoping we can figure out a solution that doesn\'t
    > involve looking at the generated .vast. It adds a bit of
    > complexity to valec.py and fountain.

### Compiling Only Needed namespaces

Today, when someone imports a namespace, we load and parse every file in
the corresponding directory. Someday, we\'ll load and parse every file
that says it belongs to that namespace, regardless of what directory
it\'s actually in.

-   **verdagon:** I don\'t like how we\'re only loading and parsing
    > **namespaces** that we actually depend on. I don\'t like writing
    > functions, hitting build, and them *not* getting compiled
    > \[because they weren\'t yet imported\]. I get that it\'s necessary
    > for the test/ requirement, but there\'s gotta be a better way.

    -   **verdagon:** maybe we can make a file extension like .test.vale
        > which causes it to only be included if we\'re doing fountain
        > test?

    -   **verdagon:** maybe we can have a directive at the top of the
        > file like test-only;?

### Can\'t Compile Multiple Test Files

To include a test, we specify it on the command line:

> \...build **stdlib.stringutils.test** stdlib:\~/stdlib/src\...

and that will include it (and its main) in the compilation.

However, we can\'t do multiple tests:

> \...build **stdlib.stringutils.test stdlib.path.test**
> stdlib:\~/stdlib/src\...

because then there would be multiple mains.

### ~~How Fountain handles C/java/javascript~~

~~Fountain sees anything that's not vale code as "other" so it places it
in native/ and lets build scripts describe what gets compiled, of course
when features are implemented, then~~ ~~build scripts can exists on a
per feature basis so only what needs to be compiled in terms of non vale
code gets compiled. This doesn't work well with how Valestrom treats
what should be compiled, as Valestrom is the one responsible for
deciding what does and doesn't get compiled, and fountain has no idea
what namespaces are and aren't compiled, thus there's no way currently
to know what C code to compile. If fountain passed something like JSON
meta data to valestrom, or could specify features to Valestrom this
could solve this issue.~~

### ~~Fountain Doesn't Mix With Current namespaces~~

~~Fountain sees complete units, which could of course be done per
namespace, but this becomes extremely tedious, because\...~~

-   ~~**cardinal:** \...a lot of adding to toml, and of course finding
    > the right toml to modify~~

    -   ~~**verdagon:** there would only be one .toml at the root, in
        > how it works today.~~

-   ~~**cardinal:** \...if per namespace .toml files where implemented
    > then every namespace would need a src directory because fountain
    > sees namespaces as root/\*.toml and src/~~

~~At the top of every file, we can have an explicit namespace statement,
to declare which namespace all of that file\'s denizens live in. For
example:~~

> ~~namespace stdlib.networking.sockets;~~

~~would say that all the denizens in this file are in the
stdlib.networking.sockets namespace.~~

-   ~~**cardinal:** This is not very original.~~

    -   ~~**verdagon:** Originality isn\'t a requirement for good
        > language design~~

-   ~~**cardinal:** Simplicity is the reason we don\'t do a rust-like
    > system.~~

    -   ~~**verdagon:** the namespace statement is optional, so it
        > doesn\'t add any burden to new users. The namespace statement
        > is really just for those who don\'t like being coupled to a
        > file system, or who want a file-specific module. I expect most
        > people won\'t use these.~~

-   ~~**cardinal:** make 1:1 with directory meaningless, either enforce
    > definition by filesystem, or building a separate module tree, like
    > rust and nickel, in between adds unneeded confusion, unless you
    > did what java does and errors if namespace.. doesn\'t match
    > directory structure, which is I find just an unneeded error~~

    -   ~~**verdagon: (**see above about the namespace statement being
        > optional)~~

~~TL;DR: when a namespace is included, we include the C files in its
native/ subdirectory.~~

-   ~~**cardinal:** conjecture this is experimental~~

    -   ~~**verdagon:** as i said at the top, \"Things that are
        > implemented are not final decisions. There\'s a lot of
        > temporary solutions related to modules.\"~~

~~If the namespace statement is missing, we infer its namespace from the
file path. If a file is in the ./networking/sockets directory inside the
stdlib project\'s source directory, we automatically infer that it\'s
part of the stdlib.networking.sockets directory.~~

-   ~~**cardinal:** fountain doesn\'t have this notion exactly, it sees
    > namespaces as anything with a fountain.toml and at least a src
    > directory~~

    -   ~~**verdagon:** I\'ve been trying to maintain a conceptual
        > separation here: fountain thinks in terms of projects, vale
        > thinks in terms of namespaces. I don\'t (yet) see a reason
        > fountain would need to know anything about namespaces or
        > imports at all, really.~~

# Requirements

These are capabilities of alternative A that we want to keep.

## 1: Inline Tests

We need to be able to have a test/ directory for a given namespace, that
is not normally compiled.

Today\'s approach accomplishes this by treating it as a namespace. That
way, we can run a test by specifying its namespace on the command line.
For example, in\...

> python3 \~/Vale/release-unix/valec.py build stdlib.stringutils.test
> stdlib:\~/stdlib/src \--output-dir build \--add-exports-include-path

\...note how we say:

> stdlib.stringutils.test

which specifies that it, and everything it directly depends on, should
be compiled.

We also want to be able to run tests from a .vmd file.

## 2: Not Compiling Entire Standard Library

For example, the following program should compile the stdlib.networking
namespace\...

> namespace myserver;
>
> import stdlib.networking.\*;
>
> fn main() export {
>
> sock = listen(\...);
>
> sock.write(\"hello!\");
>
> }

\...but shouldn\'t compile other parts of the standard library, such as
stdlib.command.

Today\'s approach accomplishes this by only compiling namespaces that we
indirectly depend on.

## 3: Fast Incremental Compilation

Today\'s approach accomplishes this by disallowing cyclic dependencies
between namespaces. This way, when we change something, the compiler can
know exactly which namespaces it has to recompile.

# Alternative B: Inline .toml

Fountain could be told to look for fountain.toml in for example
src/command/.

The root .toml would have an entry in its \[\[dependencies\]\] which has
a path to one of its subdirectories.

However this will cause some weird arguments to be passed to Valestrom
principally stdlib:/path/to/stdlib/src
command:/path/to/stdlib/src/command which I suppose could work, but is
not implemented.

# Alternative C:

(Compared to alternative B) A better solution would be to have
command:/path/to/stdlib/command/ and have command as a dependency of
stdlib, then in stdlib/src/anywhere.vale import command.\* as
stdlib.command.\*;

# Alternative D: A + Conditional Dependency

Goal: enable platform specific dependencies.

Fountain could have a condition = field for a given dependency.

\[\[dependencies\]\]

name = \"nativefilesystem\"

as = \"filesystem\"

condition = \"isNative(target_os)\"

version = \"0.1.0\"

allow_native = true

\[\[dependencies\]\]

name = \"jvmfilesystem\"

as = \"filesystem\"

condition = \"target_os == jvm\"

version = \"0.1.0\"

allow_native = true

We wouldnt have conditional compilation at the language level.

To implement features, they can specify constants via command line (like
C\'s -DSPRONGLING_ENABLED=true) and use them in if-statements:

> if (SPRONGLING_ENABLED) {\
> \...
>
> } else {
>
> \...
>
> }

Also adding a means of declaring \"these constants are options\" and a
means of having a list of constants for a given dependency such as

\`\`\`toml

\[\[dependencies\]\]

/// \... normal stuff here

constants = \[\"name=value1\", \"other=value2\"\]

\`\`\`

# Alternative E: M + A

## How it works:

With features a programmer specifies the requirements for a specific
namespace such as src/command, this is done by both including specific
external vale projects as dependencies for these features, as well as
other features in the same project as requirements of this declared
feature. Within feature there is also support for a custom imports that
only apply to that given feature, and what depends on it.

What this solves:

1.  When vale code must be compiled

2.  When .c or .java code must be compiled

3.  What things are needed for the .vale code that is compiled

4.  What other features, and thus what other vale or c is also required
    > to build the current feature for example, stdlibs command needs
    > stdlibs path in order to resolve whats in PATH environment
    > variable, features are basically project internal components that
    > can be optionally used or not used by other projects.

This system of features helps to containerize namespaces into their own
unit without requiring that each namespace have its own fountain.toml,
or src directory, this containerization can be accomplished by having
one feature correspond with only one namespace that is only one
directory. When a feature is enabled everything in the path argument
gets compiled, which benefits Alternative N

A basic example:

> \[\[features\]\]
>
> name = "path"
>
> path = "path/"

features.imports corresponds to the feature directly above it, because
this is how the toml parser I selected handles structs within structs.
So, if the above feature is enabled, this will call a command with these
args after the appropriate headers have been generated:

> \[\[features.imports\]\]
>
> command = "clang"
>
> args = \["-c", "src/path/path.c", "-o", "build/path.o", "-I./build/"\]
>
> link_args = \["./build/path.o"\]

Another example, building on the one above:

> \[\[features\]\]
>
> name = "command"
>
> path = "command/"
>
> this means that the path feature must also be enabled in order for the
> command feature to work, however currently import stdlib.path must be
> in command.vale for the exists function:
>
> requires = \["path"\]

if this feature is enabled, call this command with these args, NOTE:
features.imports

corresponds to the feature defined directly above it

> \[\[features.imports\]\]
>
> command = "clang"
>
> args = \["-c", "./src/command/subprocess.c", "-o", "build/command.o"\]
>
> link_args = \["./build/command.o"\]

Markvale as an example

> \[\[dependencies\]\]
>
> name = "stdlib"
>
> version = "0.1.0"
>
> features = \["command"\] // defined in \[\[feature\]\] name =
> "command" as above in stdlibs fountain.toml, but used in markvales
> dependency on stdlib as shown in this line

Fountain would call valestrom as

java -cp Valestrom.jar dev.vale.driver.Driver build (existing args)
stdlib:/path/to/stdlib/src \--features src/command src/path

Naming features is only needed in fountain valestrom doesn't need to
know features names

This tells Valestrom in addition to stdlib/src also compile code in
stdlib/src/command, this doesn't compile entire stdlib, because it
doesn't compile something like stdlib/src/sockets because they aren't
needed, however command can depend on path (which it does) and fountain
will pass as path to \--features of stdlib:/path/to/stdlib/src though
this isn't technically needed because command/command.vale imports
stdlib.path.\*; for example stdlib:/path/to/stdlib \--features
src/command src/path

Valestrom would know that that src/command and src/path belong to stdlib
based on them coming after stdlib, tho \--compie flag or \--test flag
would also work in place of \--features flag

Fountain.toml will have a test section as define below

> \[\[tests\]\]
>
> name = "command_test"
>
> requires = \["feature1", "feature2"\]

used for external dependencies, cargo doesn't have these inlined with
tests as cargo doesn't have tests in toml, but this would basically be
cargo's dev-dependencies

> \[\[tests.dependencies\]\]
>
> Name = "\..." // same as all other dependencies, see above in markvale

Because linker errors will occur if feature isn't enabled, fountain will
output a suggestion such as "did you forget to enable a feature?"

## Discussion

**verdagon:** Possible drawback: this is how the user is supposed to
activate the command feature, which will cause fountain to bring in the
command C code:

> \[\[dependencies\]\]
>
> name = "stdlib"
>
> version = "0.1.0"
>
> features = \["command"\]

but if the user forgets to specify a feature, like so:

> \[\[dependencies\]\]
>
> name = "stdlib"
>
> version = "0.1.0"
>
> \# note no features = \... here

then valec will succeed (all of the import statements in stdlib are
present and well-formed) but we\'ll get a linker error.

> **verdagon:** A mitigation, from cardinal: on any linker error, we can
> say \"Did you forget to enable a feature?\" with a handy explanation
> or link about it.

**verdagon:** Benefit: E is very explicit about what namespaces are
needed for a feature, so we can include only the needed namespaces,
which speeds up compilation.

# Alternative E2: E + Multi-Namespace and Forced

This builds on option E (features), which builds on A (today\'s), M
(conditional testing inclusions).

This is similar to option E, with two adjustments: features are
**multi-namespace** and **forced**.

## Multi-namespace

This means that a feature doesn\'t just correspond to a specific
namespace/directory, it includes all namespaces under the specified
path.

So when we say:

> \[\[features\]\]
>
> name = "path"
>
> path = "path/"

This means the \"path\" feature includes **all** namespaces under the
path/ directory, for example:

-   path

-   path/manipulation

-   path/finding

-   path/traversal

## Forced

This means that **a namespace cannot be included if its containing
feature is not enabled.**

If I have a vale program:

> import stdlib.path.finding;
>
> fn main() {
>
> path = Path(\".\")
>
> path.findAll(\"\*.txt\")\*.file.each(println);
>
> }

and this fountain.toml:

> \[\[dependencies\]\]
>
> name = \"stdlib\"
>
> version = \"0.1.0\"

it will **fail to compile** with this error message\...

import not found: stdlib.path

\...until we add this line:

> features = \[\"path\"\]

(note: we can improve that error message, it\'s written this way for now
for clarity, to emphasize that Valestrom literally doesn\'t know about
stdlib.path or anything in it)

## Compiler Invocation

Assuming we\'re in a project \"markvale\", and stdlib is in \~/stdlib,
the above would cause the compiler to be invoked like this:

> java -cp Valestrom.jar dev.vale.driver.Driver build markvale
> markvale:src stdlib.path:\~/stdlib/src/path \--output-dir build
> \--add-exports-include-path

The relevant argument in the above is this:

> stdlib.path:\~/stdlib/src/path

So it works just like projects do today in Valestrom, but they can have
. in them now.

# Alternative H: generate .h separately

Javac has an option to generate the .h needed for JNI, this could be
done, then only compile what is imported, more specifically, compile
everything once with a given flag, then without that flag only compile
what is needed (have the user manually handle generate this header, or
handle it in a bash script), this means that the headers used will be
fixed and stored in the git repo, this is what the master branch of
stdlib currently does with native/StrChain.h, this works perfectly well,
just needs to be regenerated for differences between vale versions

# Alternative I: use bash instead of clang, and check for files existence (very temporary)

Instead of calling clang in imports, call a bash script, this solution
is very platform dependant until platform specific imports are
implement, which should be a matter of adding a single if statement to
fountain example:

\`\`\`toml

\[\[imports\]\]

Platform = "x86_64-pc-windows-msvc"

command = "./build.bat"

args = \["src/command/"\]

\[\[imports\]\]

Platform = "unix"

Command = "./build.sh"

args = \["src/command"\]

\`\`\`

This runs code depending on the platform, specifically it runs a bash
script that handles invoicing clang if and only if the header exists

# Alternative F: A + Dependency Aliasing

This adds \"dependency aliasing\" to A.

Motivating problem:

-   namespace MyProject depends on CoolPhysics and QuickAnalysis

-   CoolPhysics depends on LinearMath version 1.0

-   QuickAnalysis depends on LinearMath version 2.0

We\'ll make it so we can say this to Valestrom:

> \...
>
> build
>
> MyProject
>
> MyProject:./src
>
> CoolPhysics:LinearMath:LinearMath1
>
> QuickAnalysis:LinearMath:LinearMath2
>
> LinearMath1:\~/linearmath1.0
>
> LinearMath2:\~/linearmath2.0
>
> \...

Note how these arguments\...

> CoolPhysics:LinearMath:LinearMath1
>
> QuickAnalysis:LinearMath:LinearMath2

\...have two colons.

This means \"CoolPhysics, when it says LinearMath, is referring to
LinearMath1.\"

This way, we\'re importing LinearMath1 and LinearMath2 as two completely
separate codebases.

# Alternative J: A + Different Platforms

## When do we link in a .jar file?

This works very similarly to how we deal with .c files in the native/
directories.

TL;DR: When the .vast shows that a namespace was included, we include
the .jar files in its jvm/ subdirectory.

Then, we feed the .vast to a program similar to Midas, but targeting the
JVM. It produces a bunch of .class files (similar to the .o that Midas
produces).

So, when we have an extern function:

> struct Socket export { fd int; }
>
> fn writeToSocket(socket &Socket, contents str) void extern;

the resulting .class files assume that that function will be present. In
other words, the eventual jar invocation assumes that we\'ll have some
Java code on the classpath that defines it:

> namespace stdlib.networking;
>
> class \_\_namespace {
>
> void writeToSocket(stdlib.Socket socket, String contents) {
>
> \...
>
> }
>
> }

The Java code and the vale code here are tightly coupled: the above vale
assumes that the above Java function exists, and the above Java function
assumes that the file stdlib.Socket class was generated. So, **if one is
included, the other must be as well.**

So, because of this, valec.py makes sure that when we compile a
namespace, we also include any of the namespace\'s jvm/ subdirectory\'s
.java files in the eventual jar invocation.

valec.py does this by looking in the externs section of the .vast, which
lists all namespaces that had externs. It will then include those java
files in the jar invocation.

## Other Languages

We\'d do something similar for other languages.

native/ contains C code

jvm/ contains java/scala/kotlin code

# Alternative K: A + M + Projects

This alternative builds upon A, and addresses these drawbacks (which are
described above):

-   **Too Many Native Directories** drawback, by only allowing native/
    > directories at the top level of a project or subprojects.

-   **Looking In Vast** drawback, by instead looking for the
    > **existence** of generated .vast files for projects/subprojects,
    > which each generate their own .vast.

-   **Compiling Only Needed namespaces** drawback, by having Valestrom
    > compile entire projects (or subprojects) when we import anything
    > from them.

These are all explained below.

(It also builds upon M, which addresses A\'s **Can\'t Compile Multiple
Test Files** drawback.)

## Projects

We\'ll introduce the notion of a **project**. A project is a directory
that contains a src/ directory which contains namespaces.

Alternative A said:

> Today, when someone imports a namespace, we load and parse every file
> in the corresponding directory.

instead, we\'ll compile on a per-project basis:

> Today, when someone imports a namespace, we load and parse every file
> in **its project\'s src/ directory.**

(Below, we\'ll explain subprojects. For those, when we import something
in a subproject, we\'ll load and parse every file in the subproject\'s
src/ directory)

## Fast Incremental Compilation

Even though Valestrom will do an initial compile for every file it knows
about, we can still do incremental compilation on a per-**namespace**
(namespace, not project) basis, or even more fine-grained eventually.

Really, this alternative doesn\'t slow down incremental compilation at
all, it\'s unrelated.

## Tests

If we compile every file in a project we import, then we\'ll conflict
with requirement #1, to not compile tests unless we\'re specifically
testing.

That\'s why this approach builds upon alternative M, which solves it for
us.

## Subprojects

If we compile every file in a project we import, then we\'ll conflict
with requirement #2, to not compile the entire standard library when we
depend on only part of it.

So we\'ll introduce the **subproject** concept.

### Defining

We can divide a project into multiple sub-projects, to be able to
compile only parts of them.

For example, mathlib\'s fountain.toml would have these subproject
declarations:

> \[\[subproject\]\]
>
> name = \"linear\"
>
> path = \"linear\"
>
> \[\[subproject\]\]
>
> name = \"beziercurves\"
>
> path = \"curves/bezier\"
>
> \[\[subproject\]\]
>
> name = \"jankycurves\"
>
> path = \"curves/janky\"

Each subproject will **have its own** src/ and native/ subdirectories. A
project with subprojects won\'t.

In other words:

-   If a project has a src/ or native/ directory, it cannot have
    > subprojects.

-   Vice versa; if a project has subprojects, it cannot have a src/ or
    > native/ directory.

### Using

The user will depend on the project like normal, no complications:

> \[\[dependencies\]\]
>
> name = \"mathlib\"
>
> version = \"1.5.2\"

(I\'d show an example that depends on stdlib, but those are depended on
automatically by Fountain, I think)

### Compiler Invocation

Assuming we\'re in a project \"markvale\", and mathlib is in \~/mathlib,
the above .toml snippets would cause the compiler to be invoked like
this:

> java -cp Valestrom.jar dev.vale.driver.Driver build markvale
> markvale:src mathlib.linear:\~/mathlib/src/linear
> mathlib.beziercurves:\~/mathlib/src/beziercurves
> mathlib.jankycurves:\~/mathlib/src/jankycurves \--output-dir build
> \--add-exports-include-path

The relevant arguments in the above are these:

> mathlib.linear:\~/mathlib/src/linear
> mathlib.beziercurves:\~/mathlib/src/beziercurves
> mathlib.jankycurves:\~/mathlib/src/jankycurves

So it works just like projects do today in Valestrom, but they can have
. in them now.

## Valestrom Outputs Separate .vast Files Per (Sub)Project

Fountain will look at what .vast files Valestrom made, to know when to
include a native/ folder (explained in the next section).

Today, Valestrom outputs a single .vast file, which contains a {
\"packages\": { \... } } map, which contains only the namespaces that
were actually imported by the user.

**Instead of doing that,** we\'ll have Valestrom generate a separate
.vast for every project and subproject that was actually imported by the
user.

So, if we had only these statements:

> import somenormalproject.\*;
>
> import mathlib.linear.\*;
>
> import mathlib.jankycurves.\*;

then it would generate these in the build/ directory:

-   somenormalproject.vast

-   mathlib_linear.vast

-   (**no** mathlib.beziercurves, because it was not imported)

-   mathlib_jankycurves.vast

## When do we Link in a .c File?

When we see a project\'s (or subproject\'s) .vast file in the build
directory, we\'ll link in all the .c files in that project\'s (or
subproject\'s) native/ directory.

Recall that Valestrom will only compile a subproject if we import it.
So, putting it together: if a user imports a subproject a subproject,
we\'ll generate a .vast for it, and will therefore compile that
subproject\'s native/ directory.

In the above example, we\'ll include in the clang invocation\...

-   somenormalproject\'s native/ directory.

-   mathlib.linear\'s native/ directory.

-   **(not** mathlib.beziercurves\'s native/ directory, **because we
    > don\'t see its .vast file)**

-   mathlib.jankycurves\'s native/ directory.

# Alternative M: Testing

This builds upon alternative A, and solves its **Can\'t Compile Multiple
Test Files** drawback (explained above).

We\'ll introduce this rule: if a file ends in .test.vale, we\'ll only
compile it when we\'re testing (in other words, when we use the
compiler\'s (or fountain\'s) test command instead of the build command.)

So, if we have these files:

-   markvale/src/sitegen/main**.vale**

-   markvale/src/parse/parser**.vale**

-   markvale/src/parse/parsing**.test.vale**

-   markvale/src/integration/ax**.test.vale**

and we run fountain build (or valec build), it will compile **the first
two** files.

If we run fountain test (or valec build-test), it will compile **all**
of them.

This would break today\'s tests, because each test has a main function,
and if we compile all the above files, we\'ll actually have three main
functions.

To resolve this, we\'ll do three things:

-   Change the export in fn main() export { \... } to entry.

-   Allow any function with any name to be an entry.

-   When there are multiple entry functions, the program\'s first
    > argument must specify which entry function to call.

These are all explained below.

## Entry

Like mentioned above, we\'ll use a different keyword, **entry**, to
decorate main. For example:

> fn main() **entry** {
>
> print(\"hello world!\");
>
> }

Entry functions are implicitly exported.

## Multiple Entry Functions

Users can put entry on any function. For example:

> fn test() int **entry** {
>
> suite = TestSuite();
>
> suite.test(\"splice\", {
>
> splice(\"foo\", 1, 1, \"lol\") should_equal \"flolo\";
>
> });
>
> ret (suite).finish();
>
> }

(Note that dependencies can have entry functions, but they will be
inaccessible unless they\'re native-whitelisted in the .toml)

The next section describes how we\'ll run a specific entry function.

## Running An Entry Function

\"If we have multiple entry functions, how do we know which one to
call?\"

When there are multiple entry functions, the user will specify it on the
command line. (This is a familiar concept to Java developers.)

For the above markvale example, these are all valid invocations:

> build/a.out markvale.parse.main
>
> build/a.out markvale.parse.test
>
> build/a.out markvale.integration.test

If the user doesn\'t specify an entry function, we\'ll print the
following output.

> Missing command line argument; there are multiple entry functions, so
> please specify the entry function you wish to call.
>
> Some examples:
>
> build/a.out markvale.parse.main
>
> build/a.out markvale.parse.test
>
> build/a.out markvale.integration.test

(we\'ll list up to 10 of them, and if there are more, we\'ll show a \...
as the 10th item)

# Alternative L:

Distribute .vast files, making source code optional.\
\
Users can define their build programs (such as for C, etc.) through
constants in the build files or through the compiler, making it
extendable and available to other build systems if the user decides to
use a different one.\
\
Instead of targeting languages (Java, C, JS, etc.), target FORMATS (.so,
.jar, .o, etc.).\
\
An interesting alternative concept (or that can be used in tandem) is
for allowing the user to define their own compilers for other languages
through the compiler itself, using plugins that the compiler calls
before OR after compiling Vale code, but this isn't necessary.

One could also define a build system, that while built for Vale, is a
separate, language-agnostic program. Example:\
\
I would recommend something like Make, where it\'s just calling
commands, then users can supply values to empty constants either in a
config or the terminal. Example (idk the internals, but this is just an
example):

./lib/namespacename/build.fountain

REQUIRE odin_compiler

REQUIRE namespacename_bin

\[build\]

odin_compiler {Module}/src -out={namespacename_bin}/namespacename.lib
-build-mode:dll

./build.fountain

SOURCE src

HEADERS include/vale

BUILD lib/namespacename

DEFINE odin_compiler = odin

DEFINE namespacename_bin = bin/namespacename

\[build\]

something with valec.py I\'ve forgotten the arguments

## Discussion

**cardinal:** this seems to basically be a new form of make, which while
interesting is somewhat redundant as the ability to define variables,
and run commands that depend on those variables is a planned to be added
to fountain, and I have started doing so in my local version (may be in
upstream I haven\'t checked), if you want to create such a language, of
course go for it, but what with make available through imports and
build.vale\'s inclusion of stdlib, as well as import\'s ability to run
any command, and the existence of build.vale, it seems to me somewhat
redundant to create a new make like language

# Alternative N: hybrid(E, K) feature inference

## How it's like option K:

Like option E fountain.toml will have a concept of features, however
partial .vast for each name space will still be generated (maybe with
that features external (other project) dependencies? Maybe node?) and
based on these partial .vast fountain will infer what to import, and
will suggest that features be written for the namespaces that are
imported, but not declared as features in toml.

## How it's like option E:

No need for per namespace (subproject) src/ native/ and fountain.toml,
and still be able to specify in fountain.toml for the entire project
various features (subprojects)

## How it will be like both:

The toml would look virtually identical wether the keyword is feature or
subproject, or perhaps most appropriately namespace, and will greatly
cut down on compile time.

For example:

\[\[namespace\]\]

name = "command"

path = "src/command"

\[\[namespace.imports\]\]

command = "clang"

// ...

## Advantages over E xor K:

This approach will allow for even more efficient incremental builds, as
there will be the advantage of having partial .vast, and being able to
know, from a single toml file exactly which cached vast files to
download, without waiting for Valestrom to build the project initially
(the toml used to declare the dependency on a given project, and set of
name spaces) (really makes the most out of caching .vast files)
