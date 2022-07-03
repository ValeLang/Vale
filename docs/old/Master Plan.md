\- test vivem to make sure its using these interfaces correctly

\- get this conformance stuff to work

\- package and clean things up so we can have a working generator for
kevin

\- get rid of void, replace with empty struct, might need inlining to
make that work

we should eventually figure out whether we want plugins and whatnot to
be able to specify astrouts or temputs or metal. right now it\'s kind of
a mix. maybe thats fine?

eventually we want the interface constructors to not return the
interface, but the subclass we just created. no reason we cant.

struct AnonymousIFunction1\<Functor1 Ref, M Mutability\>

where { M = mutIfAnyMut(Functor1) }

M {

functor1 Functor1;

}

impl\<M Mutability, A Ref, R Ref, Functor1 Ref\>

AnonymousIFunction1\<M, Functor1\>

for IFunction1\<M, A, R\>

where { exists fn \_\_call(Functor1, A)R }

{

fn invoke(&this impl, a A) R {

= this.functor1(a);

}

\...

}

maybe we want to do transactions to make sure things end up in a sane
state, such as there are no declared functions that arent implemented,
etc. maybe. it might need to be reentrant (hopefully after transaction
close, rather than inside).

# Phase 7

\- kevin: vale -\> llvm -\> wasm

\- evan: clr -\> dll

\- syntax highlighting. can be offline, can be hackish. stretch:
scala-js.

\- finish and post the site. - 3 weeks

\- make final decision on which license to use.

\- opensource the language.

# Phase 8

\- make domino open source

\- make primitive domino roguelike to demo

\- make an android/ios/web app to demo

# tech debt:

\- enforce that we dont change variables that dont have a ! after their
name

\- get rid of generatorArgsRegisters

\- get rid of generatorFunctionRegister

\- email IARC saying we\'re renaming it to vale

\- stop using void for everything! make it so vivem puts its own
destructors in at index -1 or something.

Potential bugs:

\- generateStructDestructor takes a mutable env, but
generateArraySequenceDestructor and generateUnknownSizeArrayDestructor
don\'t. what\'s that first one doing that these other two dont do?

Fixing tests:

\- Packs aren\'t remembered, they\'re probably stripped out by the
scout? let \[x\] = (4); It\'s a problem because you can\'t destructure
an int. 1 PatternTests

\- Make RoguelikeTest into an automated one somehow.

\- Add some tests that get identifying runes from just params:

\- fn add(a: #T, b: #T) gets #T

\- fn add(a: #T\<#X\>) gets #T and #X

\- fn add(a: #T\<#X\>\[a: #Q\]) gets #T and #X but not #Q

\- fn add\<#F\>(a: #T) gets #F and #T

\- Add a test or something that tests that there\'s no identifying runes
that can be known at compile time.

\- Add tests for the templata templar

Fixing checks:

\- Make hammer check for unstackified things again

\- Add check back in to make structs not contain packs.

\- Put a check in for the infinite recursion if we dont predict rune
types, see RTMHTPS

Robustness:

\- switch away from the \# for runes. - 1 week

\- improve the parser to work function by function. - 1 week

\- make the inferer always put breadcrumbs in properly instead of just
forwarding errors from below

\- make inferer errors toString put in some newlines where the errors
start

\- get rid of the automatic temporarying, which should allow us to
collapse the typetemplar and 2x templatatemplar giant switches

\- In vivem, make dropReference not duplicate the logic we do when we
call interfaces

\- get rid of share, just have own, borrow, raw (and someday weak).

Adding/fixing features:

\- Make \_\_Array a function that does a special instruction, rather
than having a special templata for it.

\- Make \_\_Array take in a lambda\... and resolve the right overload
before handing it in to the ConstructArray2.

\- Fix array indexing like this.board.(i), see ExpressionTests

\- Make it so unless the body has one statement without a semicolon or
ends with an = \...; we know it\'s Void. Could help with leaving out the
void in the \"Destroy members at right times\" test.

\- make and and or short-circuit

\- make it so we never have to think about a sequence\'s mutability\...
right now we have \[:imm 4 \* Int\] but can we infer that everywhere?

\- make toArray infer its element templata

\- right now we only have + - \* / \< \> as infix operators, allow
custom ones like \<\< and \>\>, make their precedence determined by
their first characters

Niceness:

\- use struct and interface as citizen more, there\'s sooo much
duplication.

\- Use state monad in the templata templar

\- Make a better env.toString(). Right now when we print out an
OverloadSet in NameTemplar we get a barf.

Adding features:

\- switch away from \# for runes

\- Make \"interface I:#T imm rules(#T: Ref) { }\" into \"interface
I:Ref#T imm { }\"

\- Make \"interface I:Ref#T imm { }\" into \"interface I\<Ref#T\> imm {
}\"

\- Make it so we dont have to say \"block { \... }\" for a block.

\- Make it so we can have a variadic IFunction

\- passThroughIfStruct, passThroughIfInterface, passThroughIfConcrete
could be folded into type system\...

Not sure:

\- Split hammer into two phases

\- Put all the struct member\'s rules into the top of the struct

\- Make Inferer have a special SuperclassOf constraint.

\- make it so we can have functions in structs; have calls look in their
arguments\' namespaces

\- put struct, interface, citizen, etc. in as TemplataTypes

\- instead of using double underscores, use sealed traits and case
classes

\- make a playground, using scalajs+vivem or wasm+server. - 2 weeks

# Long Term

![](media/image1.png){width="6.5in" height="4.875in"}

[[http://www.webgraphviz.com/]{.underline}](http://www.webgraphviz.com/)

digraph g {

ratio = fill;

node \[color = \"black\"\];

rankdir=LR;

basic \[label = \"Basic Compiler\"\];

\"Flatter\\nFlats\" -\> basic

\"Resurrect\\nSculptor\" -\> basic

\"Break\\nReturn\\nContinue\" -\> basic

jvm \[label = \"JVM Backend\"\];

basic -\> jvm

clr \[label = \"CLR Backend\", style = \"filled\", color = lightgray\];

jvm -\> clr

unity \[label = \"Unity\", style = \"filled\", color = lightgray\];

clr -\> unity

clragent \[label = \"CLR Agents\", style = \"filled\", color =
lightgray\];

clr -\> clragent

wasm \[label = \"WASM\", style = \"filled\", color = lightgray\];

basic -\> wasm

gdb \[label = \"GDB\"\];

basic -\> gdb

self \[label = \"Self Hosted\"\];

roguelike -\> self

parser \[label = \"Better Parser\", style = \"filled\", color =
lightgray\];

basic -\> parser

highlighter \[label = \"Syntax Highlighter\", style = \"filled\", color
= lightgray\];

basic -\> highlighter

formatter \[label = \"Auto Formatter\", style = \"filled\", color =
lightgray\];

basic -\> formatter

intellij \[label = \"IntelliJ Plugin\", style = \"filled\", color =
lightgray\];

formatter -\> intellij

self -\> intellij

parser -\> intellij

cagents \[label = \"C/C++ Agents\"\];

basic -\> cagents

sagents \[label = \"Swift Agents\", style = \"filled\", color =
lightgray\];

cagents -\> sagents

jagents \[label = \"Java Agents\"\];

jvm -\> jagents

rcelision \[label = \"RC Elision\", style = \"filled\", color =
lightgray\];

basic -\> rcelision

hero \[label = \"Hero Threads\"\];

rcelision -\> hero

immugc \[label = \"Immutable GC\", style = \"filled\", color =
lightgray\];

basic -\> immugc

console \[label = \"Better IO\"\];

basic -\> console

roguelike \[label = \"Roguelike\"\];

console -\> roguelike

ss \[label = \"Superstructures\"\];

basic -\> ss

lazyss \[label = \"Lazy SS\"\];

ss -\> lazyss

cpuparallel \[label = \"Parallel Functions\"\];

basic -\> cpuparallel

sqlfuncs \[label = \"SQL Functions\"\];

cpuparallel -\> sqlfuncs

ssfuncs -\> sqlfuncs

sqlddl -\> sqlfuncs

lazyss -\> sqlfuncs

sqlddl \[label = \"SQL tables\"\];

ss -\> sqlddl

ssfuncs \[label = \"SS Functions\"\];

ss -\> ssfuncs

chrono \[label = \"Chronobase\"\];

ss -\> chrono

fire \[label = \"Valefire\"\];

cpuparallel -\> fire

lazyss -\> fire

ssfuncs -\> fire

fireliner -\> fire

fireliner \[label = \"Fireliner\"\];

firecache -\> fireliner

firecache \[label = \"Firecache\"\];

aws \[label = \"AWS\"\];

fire -\> aws

azure \[label = \"Azure\"\];

fire -\> azure

thread \[label = \"Threading\"\];

basic -\> thread

conc \[label = \"Concurrency\"\];

basic -\> conc

async \[label = \"Async/Await\"\];

conc -\> async

networking \[label = \"Networking\"\];

conc -\> networking

remotess \[label = \"Remote SS\"\];

networking -\> remotess

ss -\> remotess

allocators \[label = \"Allocators\"\];

basic -\> allocators

}

Past phases:

phase 1: expressions. Mar 2013

phase 2: functions, overloads, llvm support. Sep 2015

phase 3: function generics v1. Jan 2016

phase 4: patterns, structs, ownership, lambdas, vivem. Mar 2018

phase 5: interfaces, virtuals. Oct 2018

phase 6: generics v2: Sep 2019
