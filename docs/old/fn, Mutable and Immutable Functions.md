Let\'s say we have a function that foreach\'s over a list of ints:

> fn foreach(list: List:Int, func: fn(Int)Void) { \... }

What is that fn(Int)Void? Is it this:

> interface IFunction:(#Params: Pack, #Rets: Pack) **imm** {
>
> fn call(params: \...#Params\...) (\...#Rets\...);
>
> }

But if we use it like this:

> fn main() {
>
> let list = List(4, 5, 6);
>
> let var numFives = 0;
>
> list.foreach({(i)
>
> if (i == 5) { mut numFives = numFives + 1; }
>
> });
>
> }

it will fail to compile, because the {(i) \... } is backed by a mutable
struct, because it has a reference to the mutable box, so it can\'t be
coerced into that **imm** IFunction. It would have compiled if IFunction
was instead mut, but we\'d have the similar problem with imm.

There are two correct yet verbose ways to do this.

#### Option A: The Java Approach

This would mean that a lambda could be coerced into an interface. We
would have two interfaces:

> interface IMutFunction:(#Params: Pack, #Rets: Pack) mut {
>
> fn call(params: \...#Params\...) (\...#Rets\...);
>
> }
>
> interface IImmFunction:(#Params: Pack, #Rets: Pack) imm {
>
> fn call(params: \...#Params\...) (\...#Rets\...);
>
> }

foreach would look like:

> fn foreach(list: List:Int, func: IMutFunction:((Int), Void)) { \... }

or, to accept immutable:

> fn foreach(list: List:Int, func: IImmFunction:((Int), Void)) { \... }

#### Option B: Mutability Rune and fn

(starting from option A)

It\'s silly to put Mut and Imm in the name, let\'s pull it out into a
rune:

> interface IFunction:(#M: Mutability, #Params: Pack, #Rets: Pack) #M {
>
> fn call(params: \...#Params\...) (\...#Rets\...);
>
> }

Nice thing about this is that we can put a rune in there, like
IFunction:#M.

And now that we\'re parameterizing that anyway, let\'s have sugar for
all this: fn:mut(Int, Bool)Str and fn:imm(Int, Bool)Str.

#### Option C: Defaulting to mut

(Starting from option B)

Some relevant things to note:

-   Even if purely functionally programming, one would still be using
    > vars, they\'re not mutually exclusive (and in fact it\'s
    > infuriating without them), which means even functional programmers
    > will use IMutFunction.

-   The more variables we capture, the more likely we\'ll be using
    > IMutFunction, since it\'s needed if there\'s even one mutable
    > field in there.

Because of this, the vast majority of functions will be IMutFunction.
For this reason, we can allow leaving off the :mut to be fn(Int,
Bool)Str.

We\'ll go with option C.
