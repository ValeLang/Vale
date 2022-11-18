# Enums

## One Value

We want to be able to say:

> enum MarkdownSpan {
>
> int;
>
> Text = 0;
>
> Compound = 1;
>
> Bold = 2;
>
> Italic = 3;
>
> Underline = 4;
>
> }

Short term, it would lower to:

> sealed interface MarkdownSpan { fn num(self impl) int; }
>
> struct Text impl MarkdownSpan { fn num(self impl) int { 0 } }
>
> struct Compound impl MarkdownSpan { fn num(self impl) int { 1 } }
>
> struct Bold impl MarkdownSpan { fn num(self impl) int { 2 } }
>
> struct Italic impl MarkdownSpan { fn num(self impl) int { 3 } }
>
> struct Underline impl MarkdownSpan { fn num(self impl) int { 4 } }
>
> interface IMarkdownSpanVisitor { \... }

Long term, if/when we have virtual members, it could lower to:

> sealed interface MarkdownSpan { num int; }
>
> struct Text impl MarkdownSpan { num int impl = 0; }
>
> struct Compound impl MarkdownSpan { num int impl = 1; }
>
> struct Bold impl MarkdownSpan { num int impl = 2; }
>
> struct Italic impl MarkdownSpan { num int impl = 3; }
>
> struct Underline impl MarkdownSpan { num int impl = 4; }
>
> interface IMarkdownSpanVisitor { \... }

Open questions:

-   Is it worth making a first-class syntax to map \"possibilities\" to
    > constants?

    -   We do it a *lot* when serializing things.

-   Would people expect Vale to have it, and judge it if it didnt?

-   Would special syntax let us optimize better perhaps?

    -   Maybe if we lowered to globals.

-   Should we lower these simple enums to globals instead?

    -   We wouldnt be able to reuse exhaustive switch checking from
        > sealed interfaces.

## Multiple Values

We could expand it to have multiple members too:

> enum MarkdownSpan{
>
> num int;
>
> weight int;
>
> Text = (0, 10);
>
> Compound(1, 55);
>
> \...
>
> }

which would (long-term) lower to:

> enum MarkdownSpan {
>
> num int;
>
> weight int;
>
> Text { num int = 0 impl; weight int = 10 impl; }
>
> Compound { num int = 1 impl; weight int = 55 impl; }
>
> Bold { num int = 2 impl; weight int = 77 impl; }
>
> Italic { num int = 3 impl; weight int = 14 impl; }
>
> Underline { num int = 4 impl; weight int = 19 impl; }
>
> }

## Varying Shapes

We\'d like to have varying shapes (like C unions, Rust enums):

> enum MarkdownSpan {
>
> Text { s str; }
>
> Compound { children List\<MarkdownSpan\>; }
>
> Bold { inner MarkdownSpan; }
>
> Italic { inner MarkdownSpan; }
>
> Underline { inner MarkdownSpan; }
>
> }

this would lower to:

> sealed interface MarkdownSpan { }
>
> struct Text impl MarkdownSpan { s str; }
>
> struct Compound impl MarkdownSpan { children List\<MarkdownSpan\>; }
>
> struct Bold impl MarkdownSpan { inner MarkdownSpan; }
>
> struct Italic impl MarkdownSpan { inner MarkdownSpan; }
>
> struct Underline impl MarkdownSpan { inner MarkdownSpan; }
>
> interface IMarkdownSpanVisitor { \... }
