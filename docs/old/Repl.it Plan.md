Repl.it is hosting a [[Programming Language Jam &
Grant]{.underline}](https://blog.repl.it/langjam), where it\'s looking
for interesting new things related to programming languages. The prize
is \$10,000, but if Vale won it, it would legitimize it as a real
upcoming language in the world\'s eyes.

Vale has been keeping its \"killer app\" up it\'s sleeve, waiting for
the right time to reveal it to the world. The ultimate goal of Vale is
to **become the gold standard for easily sharing fast code between
platforms,** which is something millions of companies worldwide
desperately need. An idea of what Vale could be is captured in [[Vision
for the Cross-Platform
Core]{.underline}](https://vale.dev/blog/cross-platform-core-vision).

Repl.it is awarding the prize \"to a team that designs and prototypes a
new language with emphasis on fresh and possibly wild ideas.\"

Vale has a very real shot at winning this competition!

And, it probably wouldn\'t take too much.

We already have two people (Verdagon for the Vale parts, Veim
transpiling to TS), and our chances of winning go up with a couple more
people to work on translating to Swift and/or Java/Kotlin.

Per the FAQ on
[[https://blog.repl.it/langjam]{.underline}](https://blog.repl.it/langjam),
the \$10,000 is divided into two chunks:

-   \$5,000 to the winners, for what they did during the three weeks.
    > This will be split up between those who worked on it during the
    > three weeks.

-   Additional \$5,000 for those who keep working on it for two months
    > after the jam.

    -   For us, this would take the form of making progress on our
        > stretch goals at the bottom.

    -   This will be split up between those who work on it for those
        > additional two months.

    -   I (Verdagon) will be doing this, but anyone can decide they\'d
        > rather not continue, and still get their part of the first
        > \$5,000.

### Deliverables

**Core Compiler** (done already!)

This is already done. The main stages of Vale\'s compiler, pre-LLVM
stage, are collectively called Valestrom, and output a very simple JSON
representation of an AST, which was specifically designed for this
purpose already.

The AST can be found at
[[Valestrom/Metal/src/net/verdagon/vale/metal]{.underline}](https://github.com/Verdagon/Vale/tree/master/Valestrom/Metal/src/net/verdagon/vale/metal),
in the files ast.scala, instructions.scala, and types.scala.

A preview of a simple program\'s AST (with some irrelevant fields taken
out):

{\"\": \"Function\",

\"prototype\": {\"\": \"Prototype\", \"name\": \"main\", \"params\":
\[\], \"return\": \"void\"},

\"block\": {\"\": \"Block\",

\"innerExpr\": {\"\": \"Consecutor\",

\"exprs\": \[

{\"\": \"Call\",

\"function\": {\"\": \"Prototype\", \"name\": \"println\", \"params\":
\[\"str\"\], \"return\": \"void\"},

\"argExprs\": \[{\"\": \"ConstantStr\", \"value\": \"hello!\"}\]},

{\"\": \"Return\",

\"sourceExpr\": {\"\": \"NewStruct\", \"sourceExprs\": \[\],
\"resultType\": \"void\"},

\"sourceType\": \"void\"}\]}}},

#### The Story (done already!)

The first deliverable will be the story about this idea\'s potential.
[[Vision for the Cross-Platform
Core]{.underline}](https://vale.dev/blog/cross-platform-core-vision)
serves this purpose, to give context about how cool this new language
could be. So, we\'re already done! (well, after some editing)

The vision describes much more than we\'re going to do for the
hackathon, and that\'s okay. Nobody expects a production-quality,
earth-shattering paradigm shift from a hackathon; they want a
*proof-of-concept* that has *potential*.

**Core Compiler Upgrade** (Verdagon)

The compiler currently produces an executable that has a main()
function. We\'ll have to make it able to export other functions too.

This isn\'t too challenging, we just have to wire up the extern keyword,
and we\'re halfway there already.

**Write a Vale Program** (Verdagon)

We\'ll make a simple Vale program, which keeps track of some state and
exposes some functions.

We\'ll make a version that runs natively, and design it to be easily
callable from another platform (we\'ll expose some nice functions to get
and set state).

**Transpiling to JS/TS** (Veim)

We\'ll transpile that app from Vale\'s AST to Typescript.

This will be separate from the core compiler, so we could do this in
whatever language we find most comfortable.

We don\'t have to transpile all of Vale, we could leave a lot out:

-   Known-size arrays

-   Weak references

-   Constraint references (we would just make these regular references)

-   Immutable structs and interfaces, we could get by with just
    > mutables.

**An App That Uses It**

The third deliverable will be a simple app that calls in from normal
code into the transpiled code. We can show that it\'s really easy to,
for example, extend an interface defined by Vale code.

### Stretch Goals

**More Platforms**

To really have something that shines, we\'d show it on all three
platforms.

We don\'t have to compile straight to JVM bytecode, and we don\'t have
to compile something that\'s binary-compatible with Swift. We can
transpile to Java, Kotlin, or Swift source code.

**The Rest of the Language**

We could implement immutable structs and interfaces, weak references,
and so on. I recommend we *don\'t* do this during the jam, and focus
instead on other things.

**Extern Generics** (verdagon)

We can add \"extern generics\" so we can use the platforms\' native
array types. This will make the resulting program a lot more elegant.
