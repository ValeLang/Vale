## Each Step Needs Template Args

ESNTA

A FullName is made of a list of NamePart, like this:

> case class NamePart(
>
> humanName: String,
>
> templateArgs: Option\[List\[ITemplata\]\])

To see why, consider this example:

> fn myFunc\<T\>(x: T) {
>
> { println(x); }();
>
> }
>
> fn main() {
>
> myFunc(5);
>
> myFunc(true);
>
> }

The myFunc\<Int\>\'s { println(x); } is a lambda backed by this struct:

> struct main:lam1 {
>
> x: Int;
>
> }

and myFunc\<Bool\>\'s { println(x); } is a lambda backed by this struct:

> struct main:lam1 {
>
> x: Int;
>
> }

but if we give this to LLVM, we\'ll have a name collision!

So, we must disambiguate them. Instead of the full name being
\"main:lam1\", we must qualify the main part. That\'s why there\'s a
templateArgs in every step of the full name and not just the end.

> FullName(List(
>
> NamePart(\"main\", Some(List(CoordTemplata(Int)))),
>
> NamePart(\"lam1\", None)))

## Need Optional Template Args in Name

NOTAN

A NameStep\'s templateArgs is an Option\[List\[ITemplata\]\], because
for example we want to disambiguate:

-   Seq which is a
    > TemplateTemplataType(RepeatedTemplataType(CoordTemplataType),
    > KindTemplataType)

-   Seq\<\> which is a KindTemplataType.
