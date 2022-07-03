sealed trait ITemplata

case class FunctionTemplata(prototype2: Prototype2)

case class ArrayTemplata()

case class StructTerryTemplata(

outerEnv: Option\[LocalEnvironment\],

struct1: Struct1,

alreadySpecifiedExplicitTemplateArgs: List\[ITemplata\])

case class InterfaceTerryTemplata(

outerEnv: Option\[LocalEnvironment\],

interface1: Interface1,

alreadySpecifiedExplicitTemplateArgs: List\[ITemplata\])

case class FunctionTerryTemplata(

outerEnv: Option\[LocalEnvironment\],

function1: Function1,

alreadySpecifiedExplicitTemplateArgs: List\[ITemplata\])

case class ValueTerryTemplata(name: String,
alreadySpecifiedTemplateArgs: List\[ITemplata\])

case class PackTemplata(members: List\[ITemplata\])

case class SequenceTemplata(members: List\[ITemplata\])

case class ReferenceTemplata(reference: Reference2)

case class ReferendTemplata(referend: Referend2)

trait IValueTemplata extends ITemplata

case class BooleanTemplata(value: Boolean)

case class IntegerTemplata(value: Integer)

case class NoneTemplata()

When we have this:

fn main() {

Array:Int(10, {\_}).4

}

The Array:Int will be a TemplateSpecifiedLookup of the Array function.

(actually, can't it be a TemplatedClosureLookup?

When we have this:

fn main() {

Array(10, {\_}).4

}

The Array will be a FunctionTerryTemplata.

Because of this, we need evaluateExpression to return ITemplatas.

When we have this:

fn doCallable(callable) {

callable();

}

fn main() {

doCallable({3});

}

we want to pass in the {3} as a struct, and then just know what \_\_call
is available for it. Two ways:

-   Lift the \_\_call into global scope so anyone can reach it if they
    > have the struct.

-   Package the struct and the function into an OrdinaryClosure2
    > referend.

-   Somehow pass in a templata which can contain these things.

what if it was all included in types? what if templatas and types were
the same thing?

what if that {3} was passed in as a OrdinaryClosureTemplata?

we could even make a pack into a templata, or a tuple into a templata,
and so on. that actually sounds kind of good.

in the late stages, we should erase all the templata stuff and lower it
all to structs and whatnot.

callable would be an OrdinaryClosureTemplata, backed by an empty struct

globalfunctiongroup would become some sort of empty struct.

if we want to pass something in as an argument to a function, it needs
to be a Type. Or, we can make functions take in Templatas as parameters.

Taking in templatas as parameters\... how do you take in "4" as a
parameter? how do you take in a specific function? I suppose you can
lower to empty structs but it's so weird.

passing in a global function group

what about lambdas' functions are thrown into the temputs rather than
the environment? easy for ordinary ones. hard for the function1's.

fn main() {

let x: \[int\|bool\|str\] = ...;

x.map(println);

}

would be awesome. in there, we're handing in 'println' which is a global
function group.

can we perhaps lower that to... x.map({ println \_; })

now, instead of a global function group, it's a lambda and all the
overloads are in a \_\_call.

now that we have this templated lambda, what do we do? how do we hand
this thing around? it has to be a TemplatedClosure2... but then we have
this *thing* which has scoutputs and stuff inside the types, gross. i
suppose thats unavoidable, itll have to be somehow.

actually, we can throw it into the environment O_o

a TemplatedClosure2 can have the \_\_call things put into its
environment. TemplatedClosure2 instead of being (structref2, function1,
env) could be (structref2, env).

what if we throw it into the temputs? the temputs can have a
Map\[StructRef2, (Function1, Env)\]. Then we can just pass these structs
around like crazy.

or, we can stick with the templatas. Temputs can have a Map\[StructRef2,
FunctionTerryTemplata\]. nice thing about temputs is that you have to
encounter the closure definition before it's ever used. or we can throw
the \_\_call into the temputs.

The problem: When we have a templated lambda, we need to put the
function1 and the environment somewhere so we can stamp it when it's
used.

Possibilities:

-   Put it in the temputs.

    -   Pros:

    -   Cons: Logic for this is spread far, we have to check the temputs
        > every time we want to call something.

-   Put it in a TemplatedClosure2(structRef, terry)

    -   Pros: Easiest approach

    -   Cons: Now we have function1 and terry and stuff in our Type2s!
        > Gross! But, we can lower them in a post-templar pass.

-   Make it so we can pass templatas as function arguments.

    -   Cons: Suddenly we can pass all sorts of mayhem as function
        > arguments.

what would it look like if we could send templatas into functions?
supposedly we'd have Parameter2(name: String, type: ITemplata)

it would destroy so much of our type safety! we might as well be
throwing around Anys! bleh.

lets try lifting the function1s. make their first param a
TypeOf1(TypeName1("\_\_Closure:main:lam1")). crap, we have to take all
the defining function's template params!

ok lets:

-   take out ievaluatedtemplata, add a
    > FunctionTerryTemplata/FunctionGroupTemplata referend. the "Array"
    > in Array(10, {\_}) will be a FunctionTerryTemplata (unless they
    > defined another Array somewhere for some reason, in which case itd
    > be a FunctionTerryTemplata)

-   allow function1s and env's into types. it sucks but we gotta.

-   add another pass at the end of templar that lowers all of these
    > things to empty structs.
