# Matching Imprecise Names Against Absolute Names

(MINAAN)

Let\'s say im in \[foo, bar\], looking for imprecise needle \[moo,
bork\].

\- If there\'s a \[moo, bork\], I should match it.

\- If there\'s a \[foo, moo, bork\], I should match it.

\- If there\'s a \[foo, bar, moo, bork\], I should match it.

\- If there\'s a \[foo, bar, soo, moo, bork\], I shouldn\'t match it.

\- If there\'s a \[bar, moo, bork\], I shouldn\'t match it.

\- If there\'s a \[foo, boo, moo, bork\], I shouldn\'t match it because
im not inside foo.boo.

\- If there\'s a \[goo, moo, bork\], I shouldn\'t match it because im
not inside goo.

\- If there\'s a \[bork\], I shouldn\'t match it.

We can\'t just add all possible init slices of the env to the needle
imprecise name,

because an imprecise name step isn\'t the same as an absolute name step;
the former

is much broader.

So, first we\'ll filter, matching structs whose name\'s end matches the
imprecise name,

and slice it off. We end up with these \"first half\"s:

\- \[\]

\- \[foo\]

\- \[foo, bar\]

\- \[foo, bar, soo\]

\- \[bar\]

\- \[foo, boo\]

\- \[goo\]

Now we filter out anything too long, leaving this:

\- \[\]

\- \[foo\]

\- \[foo, bar\]

\- \[bar\]

\- \[foo, boo\]

\- \[goo\]

At that point we compare each step to the corresponding part of the
current env \[foo, bar\],

where a missing entry in the hay is fine. That leaves:

\- \[\]

\- \[foo\]

\- \[foo, bar\]

# Templar\'s Names Are Different

TNAD

Scout\'s/Astronomer\'s name parts correspond to where they are in the
source code, but Templar\'s correspond more to what namespaces and
stamped functions / structs they\'re in.

For example,

(bork.vale)

namespace N {

fn moo\<T\>(button IButton, x T) {

button.onClick(\[x\]{ x.print(); });

}

}

there is only one \"button\" in the entire program, specifically at
bork.vale/moo@3:4/button. However, post-Templar, things are mainly
organized by namespace and stamped function. If the above moo function
was stamped for Int and Bool and Str, then there would be three
\"button\"s:

\- N/moo\<Int\>/button

\- N/moo\<Bool\>/button

\- N/moo\<Str\>/button
