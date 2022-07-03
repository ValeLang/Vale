## Patterns

Can match against literals, in switch statements.

So, why not match against them in function signatures too?

Quite useful for booleans and enums.

fn doStuff{(a true, b: bool) ...}

fn doStuff{(a false, b: bool) ...}

can even do a single arg pattern by just starting with : like

{:a:Int \_\_negateInt(a)}

and can even call pure unary functions:

doStuff{(a isEven isPositive :Int, b :bool) ...}

doStuff{(a {a % 2 == 0} {\_ \> 0} :MyInterface :MyOtherInterface, b:
bool) ...}

:Something just means is type of thing

its just space separated conditions, holy crap

can even put a conditions at the end of the pattern

doStuff{(a: Int, b: Bool){a + b == 10} ...}

holy crap, we accidentally can do a multiswitch thing...

switch(a, b)(

{(3, 4) ...},

{(3, 2) ...})

todo: look at "sealed"
[[https://docs.scala-lang.org/tour/pattern-matching.html]{.underline}](https://docs.scala-lang.org/tour/pattern-matching.html)

callables with patterns are actually something called "maybecallables"

{:5 10} is a MaybeCallable:(\[Int\], \[Int\])

A MaybeCallable is callable, when called itll just run that function and
if it doesnt match itll throw... but a MaybeCallable also has a member
called matcher, which returns an ?()Int, which is an optional (None if
the match didnt go through) which when some, calls a function with all
the captures.

{:5 10} makes:

{:a:Int

if (a == 5) {

10

} else {

throw "no!";

}

}

{a:Int

if (a == 5) {

Some({\[a\]() 10})

} else {

None()

}

}

make it so switch only expects MaybeCallables.
