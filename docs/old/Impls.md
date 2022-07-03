## Impls Must Be with Parent or Child

IMBPC

Let\'s say we have a simple struct implementing a simple interface:

> interface MyInterface {
>
> fn go(i: Int)Void;
>
> }
>
> struct MyStruct { \... }
>
> impl MyStruct for MyInterface { \... }

but those three things are in different places.

We a function somewhere that checks whether something is a descendant of
another thing:

> fn doSomething\<#I\>(s: #S) where(#S \< #I) {
>
> \...
>
> }

and we call it:

> fn main() {
>
> doSomething\<MyInterface\>(MyStruct());
>
> }

How will that rule know how to check whether MyStruct is a MyInterface?
In this case it\'s easy since they\'re all in the global environment.

But what about closures?

> fn main() {
>
> doSomething\<MyInterface\>(MyInterface({(x) println(x)}));
>
> }

These aren\'t in the global environment. So, we must look into the
environments of #S and #I here.

Since we\'re looking in the environment of #S and #I, it naturally
follows that the impl must be in at least one of those environments (or
part of the definition itself).

## Can Never Know All Descendants

CNKAD

One can never know, at a given time, all the descendants for a given
interface.

At any given time, someone can create a new descendant to an interface.
Even inside a function. Even inside a templated lambda!

> fn main() {
>
> let myLam =
>
> {(x) doOtherThings(ISomething({(y) println(y);}));
>
> \...
>
> }

However, at any given time, one can know all the ancestors, see IMBPC.

## Downcasting Owning Ref Produces A Result

(DORPAR)

When we downcast something, one would think we produce an option.

ISpaceship downcasted to Opt\<Firefly\> would produce either a None or a
Some(Firefly).

But if it produces a None, what happened to the original ISpaceship?

We can\'t just drop it, we might not have the args for it.

So, we need to return a Result\<Firefly, ISpaceship\>.
